function exportTable(all) {
    const incidents = JSON.parse(sessionStorage.getItem('selectedIncidents') || '[]');

    if (!all && incidents.length == 0) {
        alert('Must select at least one incident to export.');
        return;
    }

    
    fetch(`/reports/export?all=${all}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json'},
        body: JSON.stringify(incidents)
    }).then(response => response.json())
    .then(rows => {
        if (rows.length == 0) {
            alert('No Incidents match search.');
            return;
        }
        const sheet = XLSX.utils.json_to_sheet(rows);
        const book = XLSX.utils.book_new();
        XLSX.utils.book_append_sheet(book, sheet, 'Reports');
        XLSX.writeFile(book, 'Reports.xlsx');
    }).catch(error => {
        console.log('Error while Exporting', error)
        alert('Error while exporting. Please try again.');
    })
}