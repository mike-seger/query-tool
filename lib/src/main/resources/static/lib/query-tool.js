class QueryManager {
    static apiRoot = "api/query-tool"
    constructor() {
        this.configuration = null
        this.queries = null
        this.poll = {
            defaultMs: 500,
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
            document.getElementById('run-query-btn').addEventListener('click', () => this.runQuery(false, true))
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
        document.getElementById('db-type').innerHTML = `(${configuration.dbType})`
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
                    option.textContent = key.replace(/^(. )?!?[a-z0-9]+:/, "$1")
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
                await this.runQuery(false, false)
            }
        } catch (error) {
            console.error('Error executing query:', error)
        }
    }

    wait(ms = this.poll.defaultMs) { return new Promise(resolve => { setTimeout(resolve, ms) }) }

    async pollCurrentQuery(activate, useSqlInput) {
        this.poll.active = activate
        if(this.poll.started) return
        this.poll.started = true
        while (this.poll.active) {
            await this.wait()
            await this.runQuery(true, useSqlInput)
        }
        this.poll.started = false
    }

    async runQuery(skipHistory = false, useSqlInput = false) {
        const sqlTextarea = document.getElementById('sql-textarea')
        const sql = sqlTextarea.value
        const selectedQuery = document.getElementById('queries').selectedIndex
        const queryName = Object.keys(this.queries)[selectedQuery]
        const query = this.queries[queryName]

        try {
            const isCustom = this.configuration.customQueries &&
                ((!query && query?.sql !== sql) || useSqlInput)
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

            this.pollCurrentQuery(!isCustom && query?.minTTL, useSqlInput)

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
            this.runQuery(true, true)
        }
    }
}

const queryManager = new QueryManager()