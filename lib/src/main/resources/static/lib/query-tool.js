class QueryManager {
    static apiRoot = "api/query-tool"
    constructor() {
        this.configuration = null
        this.queries = null
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
        if(!configuration.customQueries) {
            document.getElementById('history-controls').style.display = "none"
            document.getElementById('sql-textarea').readOnly = "false"
        }
    }

    async loadQueries() {
        try {
            const response = await fetch(`${QueryManager.apiRoot}/queries`)
            const queries = await response.json()
            const listBox = document.getElementById('queries')
            listBox.innerHTML = Object.entries(queries)
                .map(([key, value]) => `<option value="${value.trim()}">${key}</option>`)
                .join('')

            listBox.addEventListener('change', async () => {
                await this.updateSQLAndExecute(listBox.value)
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

    async runQuery(skipHistory = false) {
        const sqlTextarea = document.getElementById('sql-textarea')
        const sql = sqlTextarea.value
        const selectedQuery = document.getElementById('queries').selectedIndex
        const queryName = Object.keys(this.queries)[selectedQuery]

        try {
            const response = this.configuration.customQueries?
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

            if (!skipHistory) this.addToSQLHistory(sql)
        } catch (error) {
            this.displayErrorResult(error)
            console.error('Error running query:', error)
        }
    }

    displayErrorResult(error) {
        const table = document.getElementById('results-table')
        table.innerHTML = `<tr><td><pre>${error}</pre></td></tr>`
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
        const headerRow = '<tr>' + headers.map(header => `<th class="literal">${this.unescape(header)}</th>`).join('') + '</tr>'
        table.innerHTML += headerRow

        for (let i = 1; i < rows.length; i++) {
            const rowData = rows[i].split('\t')
            const row = '<tr>' + rowData.map(cell => `<td class="literal">${this.unescape(cell)}</td>`).join('') + '</tr>'
            table.innerHTML += row
        }
    }

    addToSQLHistory(sql) {
        let history = JSON.parse(localStorage.getItem('sqlHistory')) || []
        const standardQueries = Array.from(document.getElementById('queries').options).map(option => option.value)

        if (!history.includes(sql) && !standardQueries.includes(sql)) {
            history.unshift(sql)
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

        historyListbox.innerHTML = history.map((sql, index) => {
            const displayValue = sql.trim()
                .replace(/  */g, ' ').replace(/\n/g, ' ').slice(0, 50)
            const prefix = String(index + 1).padStart(2, '0')
            return `<option value="${sql}">${prefix} ${displayValue}</option>`
        }).join('')
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
        if (selectedSQL) {
            document.getElementById('sql-textarea').value = selectedSQL
            this.runQuery(true)
        }
    }
}

const queryManager = new QueryManager()