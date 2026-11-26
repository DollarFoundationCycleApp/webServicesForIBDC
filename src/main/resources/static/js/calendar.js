(function () {
    var button = document.getElementById('calendar-button');
    var popup = document.getElementById('calendar-popup');
    var title = document.getElementById('calendar-title');
    var days = document.getElementById('calendar-days');
    var rangeText = document.getElementById('date-range-text');
    var allTimeButton = document.getElementById('calendar-all-time');
    var startTimeInput = document.getElementById('calendar-start-time');
    var stopTimeInput = document.getElementById('calendar-stop-time');
    var anyTimeButton = document.getElementById('calendar-any-time');
    var shownMonth = new Date();
    shownMonth.setDate(1);
    // Default range is the current month so far: the 1st through today
    var now = new Date();
    var startDate = new Date(now.getFullYear(), now.getMonth(), 1);
    var stopDate = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    // Time-of-day window as minutes after midnight, applied on every day of the range; null means any time
    var startTime = null;
    var stopTime = null;

    // Lets pages react to a completed range; stopDate is inclusive.
    // "All time" is announced with both dates null; "any time of day" with both times null.
    // detail.includes(time) tells a page whether a timestamp (ms) falls inside the dates and the time window.
    function announceRange() {
        // Snapshot the selection so later clicks don't change a range a page already received
        var from = null;
        var until = null;
        if (startDate !== null) {
            from = startDate.getTime();
            // stopDate is inclusive, so include everything up to the start of the following day
            until = new Date(stopDate.getFullYear(), stopDate.getMonth(), stopDate.getDate() + 1).getTime();
        }
        var fromMinute = startTime;
        var untilMinute = stopTime;

        function includes(time) {
            if (from !== null && (time < from || time >= until)) {
                return false;
            }
            if (fromMinute === null) {
                return true;
            }
            var date = new Date(time);
            var minute = date.getHours() * 60 + date.getMinutes();
            if (fromMinute <= untilMinute) {
                return minute >= fromMinute && minute <= untilMinute;
            }
            // Window wraps past midnight, e.g. 10:00 PM - 2:00 AM
            return minute >= fromMinute || minute <= untilMinute;
        }

        document.dispatchEvent(new CustomEvent('daterangechange', {
            detail: {
                startDate: startDate,
                stopDate: stopDate,
                startTime: startTime,
                stopTime: stopTime,
                includes: includes
            }
        }));
    }

    // "HH:mm" from a time input to minutes after midnight, or null when empty
    function parseTime(text) {
        if (!text) {
            return null;
        }
        var parts = text.split(':');
        return Number(parts[0]) * 60 + Number(parts[1]);
    }

    // "2" or "5:30", without AM/PM
    function clockText(minutes) {
        var hour = Math.floor(minutes / 60) % 12;
        if (hour === 0) {
            hour = 12;
        }
        var text = String(hour);
        if (minutes % 60 !== 0) {
            text += ':' + String(minutes % 60).padStart(2, '0');
        }
        return text;
    }

    function periodText(minutes) {
        if (minutes < 12 * 60) {
            return 'AM';
        }
        return 'PM';
    }

    // "2–6 AM", or "10 PM–2 AM" when the times are in different halves of the day
    function timeRangeText() {
        if (startTime === stopTime) {
            return clockText(startTime) + ' ' + periodText(startTime);
        }
        if (periodText(startTime) === periodText(stopTime)) {
            return clockText(startTime) + '–' + clockText(stopTime) + ' ' + periodText(startTime);
        }
        return clockText(startTime) + ' ' + periodText(startTime) + '–' + clockText(stopTime) + ' ' + periodText(stopTime);
    }

    // "Oct 1–31", "Oct 28 – Nov 3", with the year only when it isn't the current one
    function dateRangeText() {
        var thisYear = new Date().getFullYear();
        if (stopDate === null) {
            return formatDate(startDate, startDate.getFullYear() !== thisYear) + ' –';
        }
        var sameYear = startDate.getFullYear() === stopDate.getFullYear();
        if (!sameYear) {
            return formatDate(startDate, true) + ' – ' + formatDate(stopDate, true);
        }
        var text;
        if (startDate.getMonth() === stopDate.getMonth()) {
            text = formatDate(startDate, false);
            if (startDate.getDate() !== stopDate.getDate()) {
                text += '–' + stopDate.getDate();
            }
        } else {
            text = formatDate(startDate, false) + ' – ' + formatDate(stopDate, false);
        }
        if (startDate.getFullYear() !== thisYear) {
            text += ', ' + startDate.getFullYear();
        }
        return text;
    }

    // Dates (or "All time"), plus the time window when one is set: "Oct 1–31 · 2–6 AM"
    function showRangeText() {
        var text = 'All time';
        if (startDate !== null) {
            text = dateRangeText();
        }
        if (startTime !== null) {
            text += ' · ' + timeRangeText();
        }
        rangeText.textContent = text;
    }

    // The window applies once both times are filled in; clearing either one removes it
    function changeTimes() {
        var newStart = parseTime(startTimeInput.value);
        var newStop = parseTime(stopTimeInput.value);
        if (newStart === null || newStop === null) {
            newStart = null;
            newStop = null;
        }
        if (newStart === startTime && newStop === stopTime) {
            return;
        }
        startTime = newStart;
        stopTime = newStop;
        showRangeText();
        // Wait for the stop date if a new date range is half picked
        if (startDate === null || stopDate !== null) {
            announceRange();
        }
    }

    startTimeInput.addEventListener('change', changeTimes);
    stopTimeInput.addEventListener('change', changeTimes);

    anyTimeButton.addEventListener('click', function () {
        startTimeInput.value = '';
        stopTimeInput.value = '';
        changeTimes();
    });

    // First click sets the start date, second sets the stop date, third starts a new range
    function selectDate(date) {
        allTimeButton.setAttribute('aria-pressed', 'false');
        if (startDate === null || stopDate !== null) {
            startDate = date;
            stopDate = null;
        } else if (date < startDate) {
            stopDate = startDate;
            startDate = date;
        } else {
            stopDate = date;
        }
        showRangeText();
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

    // "Oct 1", or "Oct 1, 2025" with the year
    function formatDate(date, withYear) {
        var text = date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
        if (withYear) {
            text += ', ' + date.getFullYear();
        }
        return text;
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

    // Clears the range so pages show every incident; clicking a day afterwards starts a new range
    allTimeButton.addEventListener('click', function () {
        startDate = null;
        stopDate = null;
        showRangeText();
        allTimeButton.setAttribute('aria-pressed', 'true');
        highlightSelection();
        announceRange();
        setOpen(false);
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

    // "yyyy-MM-dd" as a local date, matching the dates of the calendar cells
    function parseIsoDate(text) {
        var parts = text.split('-');
        return new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
    }

    // Each page picks its starting range: the heatmap starts on "All time", other pages on the current month.
    // A page filtered on the server (reports) passes its range in so the calendar shows it after reloading.
    var dateRange = document.getElementById('date-range');
    if (dateRange.dataset.defaultRange === 'all-time') {
        startDate = null;
        stopDate = null;
        allTimeButton.setAttribute('aria-pressed', 'true');
    } else if (dateRange.dataset.rangeStart && dateRange.dataset.rangeStop) {
        startDate = parseIsoDate(dateRange.dataset.rangeStart);
        stopDate = parseIsoDate(dateRange.dataset.rangeStop);
        shownMonth = new Date(startDate.getFullYear(), startDate.getMonth(), 1);
    }
    // A server-filtered page (reports) also passes back its time window as "HH:mm"
    if (dateRange.dataset.timeStart && dateRange.dataset.timeStop) {
        startTimeInput.value = dateRange.dataset.timeStart;
        stopTimeInput.value = dateRange.dataset.timeStop;
        startTime = parseTime(dateRange.dataset.timeStart);
        stopTime = parseTime(dateRange.dataset.timeStop);
    }
    showRangeText();
    announceRange();
})();