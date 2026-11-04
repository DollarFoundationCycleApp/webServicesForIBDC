(function () {
    var button = document.getElementById('calendar-button');
    var popup = document.getElementById('calendar-popup');
    var title = document.getElementById('calendar-title');
    var days = document.getElementById('calendar-days');
    var rangeText = document.getElementById('date-range-text');
    var shownMonth = new Date();
    shownMonth.setDate(1);
    // Default range is the whole current month
    var startDate = new Date(shownMonth.getFullYear(), shownMonth.getMonth(), 1);
    var stopDate = new Date(shownMonth.getFullYear(), shownMonth.getMonth() + 1, 0);

    // Lets pages react to a completed range; stopDate is inclusive
    function announceRange() {
        document.dispatchEvent(new CustomEvent('daterangechange', {
            detail: { startDate: startDate, stopDate: stopDate }
        }));
    }

    // First click sets the start date, second sets the stop date, third starts a new range
    function selectDate(date) {
        if (startDate === null || stopDate !== null) {
            startDate = date;
            stopDate = null;
        } else if (date < startDate) {
            stopDate = startDate;
            startDate = date;
        } else {
            stopDate = date;
        }
        var text = formatDate(startDate) + ' - ';
        if (stopDate !== null) {
            text += formatDate(stopDate);
        }
        rangeText.textContent = text;
        highlightSelection();
        if (stopDate !== null) {
            announceRange();
        }
    }

    // Updates classes in place so the clicked cell stays in the DOM and the popup stays open
    function highlightSelection() {
        var cells = days.querySelectorAll('.calendar-day');
        for (var i = 0; i < cells.length; i++) {
            var time = Number(cells[i].dataset.time);
            var isStart = startDate !== null && time === startDate.getTime();
            var isStop = stopDate !== null && time === stopDate.getTime();
            var inRange = stopDate !== null && time > startDate.getTime() && time < stopDate.getTime();
            cells[i].classList.toggle('range-end', isStart || isStop);
            cells[i].classList.toggle('in-range', inRange);
        }
    }

    function formatDate(date) {
        return date.toLocaleDateString('en-US', { month: '2-digit', day: '2-digit', year: '2-digit' });
    }

    function render() {
        var year = shownMonth.getFullYear();
        var month = shownMonth.getMonth();
        var today = new Date();
        var firstWeekday = new Date(year, month, 1).getDay();
        var dayCount = new Date(year, month + 1, 0).getDate();

        title.textContent = shownMonth.toLocaleString('en-US', { month: 'long', year: 'numeric' });
        days.innerHTML = '';

        for (var blank = 0; blank < firstWeekday; blank++) {
            days.appendChild(document.createElement('span'));
        }

        for (var day = 1; day <= dayCount; day++) {
            var cell = document.createElement('span');
            cell.className = 'calendar-day';
            cell.textContent = day;
            if (year === today.getFullYear() && month === today.getMonth() && day === today.getDate()) {
                cell.classList.add('today');
            }
            var date = new Date(year, month, day);
            cell.dataset.time = date.getTime();
            cell.addEventListener('click', selectDate.bind(null, date));
            days.appendChild(cell);
        }
        highlightSelection();
    }

    function setOpen(open) {
        popup.hidden = !open;
        button.setAttribute('aria-expanded', String(open));
    }

    function changeMonth(amount) {
        shownMonth.setMonth(shownMonth.getMonth() + amount);
        render();
    }

    button.addEventListener('click', function () {
        if (popup.hidden) {
            render();
        }
        setOpen(popup.hidden);
    });

    document.getElementById('calendar-prev').addEventListener('click', function () {
        changeMonth(-1);
    });

    document.getElementById('calendar-next').addEventListener('click', function () {
        changeMonth(1);
    });

    // Close when clicking outside the date range box or pressing Escape
    document.addEventListener('click', function (event) {
        if (!button.parentElement.contains(event.target)) {
            setOpen(false);
        }
    });

    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape') {
            setOpen(false);
        }
    });

    rangeText.textContent = formatDate(startDate) + ' - ' + formatDate(stopDate);
    announceRange();
})();