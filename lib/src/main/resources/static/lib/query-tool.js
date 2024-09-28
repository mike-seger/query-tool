class QueryManager {
    static apiRoot = "api/query-tool"
    constructor() {
        this.configuration = null
        this.queries = null
        this.poll = {
            started: false,
            active: false
        }
        this.init()
    }

    async init() {
        document.addEventListener('DOMContentLoaded', async () => {
            await this.loadConfiguration()
            this.configure()

            await this.loadQueries()
            this.loadSQLHistory()

            const listBox = document.getElementById('queries')
            if (listBox.value) {
                await this.updateSQLAndExecute(listBox.value)
            }

            // Add event listeners
            document.getElementById('run-query-btn').addEventListener('click', () => this.runQuery())
            document.getElementById('delete-entry-btn').addEventListener('click', () => this.deleteHistoryEntry())
            document.getElementById('clear-history-btn').addEventListener('click', () => this.clearHistory())
            document.getElementById('query-history').addEventListener('change', () => this.selectHistoryEntry())
        })
    }

    async loadConfiguration() {
        try {
            const response = await fetch(`${QueryManager.apiRoot}/configuration`);
            if (!response.ok) {
                throw new Error('Failed to load configuration');
            }
            this.configuration = await response.json();
            console.log('Configuration loaded:', this.configuration);
        } catch (error) {
            console.error('Error loading configuration:', error);
        }
    }

    configure() {
        const configuration = this.configuration
        if(configuration.customQueries) {
            document.getElementById('history-controls').style.display = "block"
            document.getElementById('sql-textarea').readOnly = false
        } else {
            document.getElementById('history-controls').style.display = "none"
            document.getElementById('sql-textarea').style.display = "none"
        }
    }

    async loadQueries() {
        try {
            const response = await fetch(`${QueryManager.apiRoot}/queries`)
            const queries = await response.json()
            const listBox = document.getElementById('queries')
            Object.entries(queries)
                .forEach(([key, value]) => {
                    const option = document.createElement('option')
                    option.value = value.sql
                    option.textContent = key
                    listBox.appendChild(option)
                })

            listBox.addEventListener('change', async () => {
                if(listBox.value) await this.updateSQLAndExecute(listBox.value)
            })

            this.queries = queries
        } catch (error) {
            console.error('Error loading query keys:', error)
        }
    }

    async updateSQLAndExecute(sql) {
        try {
            const sqlTextarea = document.getElementById('sql-textarea')
            if (sql) {
                sqlTextarea.value = sql
                await this.runQuery()
            }
        } catch (error) {
            console.error('Error executing query:', error)
        }
    }

    parseIso8601Duration(iso8601Duration) {
        const iso8601DurationRegex = /(-)?P(?:([.,\d]+)Y)?(?:([.,\d]+)M)?(?:([.,\d]+)W)?(?:([.,\d]+)D)?T{0,1}(?:([.,\d]+)H)?(?:([.,\d]+)M)?(?:([.,\d]+)S)?/;
        const matches = iso8601Duration.match(iso8601DurationRegex);
        return {
            sign: matches[1] === undefined ? '+' : '-',
            years: matches[2] === undefined ? 0 : Number(matches[2]),
            months: matches[3] === undefined ? 0 : Number(matches[3]),
            weeks: matches[4] === undefined ? 0 : Number(matches[4]),
            days: matches[5] === undefined ? 0 : Number(matches[5]),
            hours: matches[6] === undefined ? 0 : Number(matches[6]),
            minutes: matches[7] === undefined ? 0 : Number(matches[7]),
            seconds: matches[8] === undefined ? 0 : Number(matches[8])
        }
    }

    iso8601Duration2Seconds(iso8601Duration) {
        const d = parseIso8601Duration(iso8601Duration)
        const f = d.sign === '-'? -1:1
        const ds = 24*60*60
        return f * (d.years*365*ds + d.months*30*ds + d.weeks*7*ds +
            d.days*ds + d.hours*60*60 + d.minutes*60 + d.seconds )
    }

    wait(ms = 500) { return new Promise(resolve => { setTimeout(resolve, ms) }) }

    async pollCurrentQuery(activate) {
        this.poll.active = activate
        if(this.poll.started) return
        this.poll.started = true
        while (this.poll.active) {
            await this.wait()
            await this.runQuery(true)
        }
        this.poll.started = false
    }

    async runQuery(skipHistory = false) {
        const sqlTextarea = document.getElementById('sql-textarea')
        const sql = sqlTextarea.value
        const selectedQuery = document.getElementById('queries').selectedIndex
        const queryName = Object.keys(this.queries)[selectedQuery]
        const query = this.configuration.queries[queryName]

        //console.log(queryName, query)

        try {
            const isCustom = this.configuration.customQueries && query.sql !== sql
            const response = isCustom?
                await fetch(`${QueryManager.apiRoot}/query`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'text/plain',
                    },
                    body: sql
                })
                :
                await fetch(`api/query-tool/query/${queryName}`)

            if (!response.ok) {
                throw new Error(await response.text())
            }

            const tsvData = await response.text()
            this.displayResultsAsTable(tsvData)

            this.pollCurrentQuery(!isCustom)

            if (!skipHistory && isCustom) this.addToSQLHistory(sql)
        } catch (error) {
            console.error('Error running query:', error)
            this.displayErrorResult(error)
        }
    }

    displayErrorResult(error) {
        const table = document.getElementById('results-table')
        table.innerHTML = `<tr><td><pre>${this.escapeHtml(error)}</pre></td></tr>`
    }

    escapeHtml(str) {
        return str
            .replace(/&/g, "&amp;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
    }

    unescape(s) {
        return s?.trim().replace(/\\t/g, "\t").replace(/\\n/g, "\n")
    }

    displayResultsAsTable(tsvData) {
        const table = document.getElementById('results-table')
        table.innerHTML = ''

        if (!tsvData.trim()) {
            table.innerHTML = '<tr><td>No results found</td></tr>'
            return
        }

        const rows = tsvData.trim().split('\n')
        const headers = rows[0].split('\t')
        const headerRow = '<tr>' + headers.map(header => `<th class="literal">${this.escapeHtml(this.unescape(header))}</th>`).join('') + '</tr>'
        table.innerHTML += headerRow

        for (let i = 1; i < rows.length; i++) {
            const rowData = rows[i].split('\t')
            const row = '<tr>' + rowData.map(cell => `<td class="literal">${this.escapeHtml(this.unescape(cell))}</td>`).join('') + '</tr>'
            table.innerHTML += row
        }
    }

    addToSQLHistory(sql) {
        const trimmedSql = sql.trim()
        let history = JSON.parse(localStorage.getItem('sqlHistory')) || []
        const standardQueries = Array.from(document.getElementById('queries').options).map(option => option.value)

        if (!history.includes(trimmedSql) && !standardQueries.includes(sql)) {
            history.unshift(trimmedSql)
        }

        if (history.length > 50) {
            history = history.slice(0, 50)
        }

        localStorage.setItem('sqlHistory', JSON.stringify(history))
        this.updateSQLHistoryListbox()
    }

    loadSQLHistory() {
        const history = JSON.parse(localStorage.getItem('sqlHistory')) || []
        this.updateSQLHistoryListbox(history)
    }

    updateSQLHistoryListbox() {
        const history = JSON.parse(localStorage.getItem('sqlHistory')) || []
        const historyListbox = document.getElementById('query-history')
        historyListbox.innerHTML = ''
        const option0 = document.createElement('option')
        option0.value = ""
        option0.textContent = ""
        historyListbox.appendChild(option0)
        history.forEach((sql, index) => {
            const displayValue = sql.trim()
                .replace(/  */g, ' ').replace(/\n/g, ' ').slice(0, 50)
            const prefix = String(index + 1).padStart(2, '0')
            const option = document.createElement('option')
            option.value = sql
            option.textContent = `${prefix} ${displayValue}`
            historyListbox.appendChild(option)
        })
    }

    clearHistory() {
        localStorage.setItem('sqlHistory', "[]")
        this.updateSQLHistoryListbox()
    }

    deleteHistoryEntry() {
        const historyListbox = document.getElementById('query-history')
        const selectedSQL = historyListbox.value

        if (selectedSQL) {
            let history = JSON.parse(localStorage.getItem('sqlHistory')) || []
            history = history.filter(sql => sql !== selectedSQL)
            localStorage.setItem('sqlHistory', JSON.stringify(history))
            this.updateSQLHistoryListbox()
        }
    }

    selectHistoryEntry() {
        const selectedSQL = document.getElementById('query-history').value
        if (selectedSQL && selectedSQL !== '') {
            document.getElementById('sql-textarea').value = selectedSQL
            this.runQuery(true)
        }
    }
}

const queryManager = new QueryManager()