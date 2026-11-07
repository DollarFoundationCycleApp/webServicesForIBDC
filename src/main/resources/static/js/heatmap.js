(function () {
    var mapElement = document.getElementById('map');
    var status = document.getElementById('map-status');
    var styleUrl = mapElement.dataset.styleUrl;
    var center = [Number(mapElement.dataset.centerLon), Number(mapElement.dataset.centerLat)];
    var zoom = Number(mapElement.dataset.zoom);

    // Dev-only fallback when no self-hosted style is configured
    function devStyle() {
        return {
            version: 8,
            sources: {
                osm: {
                    type: 'raster',
                    tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],
                    tileSize: 256,
                    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                }
            },
            layers: [{
                id: 'osm',
                type: 'raster',
                source: 'osm',
                paint: {
                    'raster-saturation': -0.85,
                    'raster-contrast': -0.15
                }
            }]
        };
    }

    function showStatus(text) {
        status.textContent = text;
        status.hidden = text === '';
    }

    if (typeof maplibregl === 'undefined') {
        showStatus('Map library failed to load.');
        return;
    }

    var map = new maplibregl.Map({
        container: mapElement,
        style: styleUrl || devStyle(),
        center: center,
        zoom: zoom,
        attributionControl: false
    });
    map.addControl(new maplibregl.NavigationControl(), 'top-right');
    map.addControl(new maplibregl.AttributionControl({
        compact: false,
        customAttribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
    }));

    // [longitude, latitude], the order MapLibre expects
    var incidentLocations = incidents.map(function (incident) {
        return [incident.longitude, incident.latitude];
    });

    // Popup body built with textContent so incident text is never treated as HTML
    function incidentPopupContent(incident) {
        var driverPresent = 'No';
        if (incident.driverPresent) {
            driverPresent = 'Yes';
        }
        var rows = []
        rows.push(['Reported', incident.createdTime]);
        rows.push(['Location', incident.latitude.toFixed(4) + ', ' + incident.longitude.toFixed(4)]);
        rows.push(['Injury severity', incident.injurySeverity]);

        if (!incident.hideInfo) {
            rows.push(['License plate', incident.licensePlate]);
        }
        rows.push(['Vehicle', incident.vehicleYear + ' ' + incident.vehicleMake + ' ' + incident.vehicleModel]);
        rows.push(['Vehicle color', incident.vehicleColor]);
        rows.push(['Driver present', driverPresent]);
         if (!incident.hideInfo) {
            rows.push(['Driver information', incident.driverInformation || 'None given']);
            rows.push(['Comment', incident.extraComment || 'None']);
            rows.push(['Image ID', incident.imageId]);
         }


        var content = document.createElement('div');
        var title = document.createElement('a');
        title.className = 'incident-popup-title';
        title.textContent = 'Incident';
        title.href = "/incident/" + incident.incidentId;
        content.appendChild(title);

        var list = document.createElement('dl');
        rows.forEach(function (row) {
            var label = document.createElement('dt');
            label.textContent = row[0];
            var value = document.createElement('dd');
            value.textContent = row[1];
            list.appendChild(label);
            list.appendChild(value);
        });
        content.appendChild(list);
        return content;
    }

    // Pin each incident, shown only when zoomed in to street level. Clicking a pin opens its popup.
    var pinMinZoom = 16;
    var pinsShown = false;
    var incidentMarkers = incidents.map(function (incident, index) {
        var popup = new maplibregl.Popup({ className: 'incident-popup', offset: 25, maxWidth: '280px' })
            .setDOMContent(incidentPopupContent(incident));
        return new maplibregl.Marker().setLngLat(incidentLocations[index]).setPopup(popup);
    });

    // Calendar date range; only incidents inside it are shown. Set by the 'daterangechange' event.
    var incidentTimes = incidents.map(function (incident) {
        return new Date(incident.createdIso).getTime();
    });
    var rangeStart = null;
    var rangeEnd = null;

    function inRange(index) {
        if (rangeStart === null) {
            return true;
        }
        return incidentTimes[index] >= rangeStart && incidentTimes[index] < rangeEnd;
    }

    function updatePins(force) {
        var showPins = map.getZoom() >= pinMinZoom;
        if (showPins === pinsShown && force !== true) {
            return;
        }
        incidentMarkers.forEach(function (marker, index) {
            if (showPins && inRange(index)) {
                marker.addTo(map);
            } else {
                marker.remove();
            }
        });
        pinsShown = showPins;
    }
    map.on('zoom', updatePins);

    function heatmapData() {
        var features = [];
        incidentLocations.forEach(function (location, index) {
            if (inRange(index)) {
                features.push({
                    type: 'Feature',
                    geometry: { type: 'Point', coordinates: location },
                    properties: {}
                });
            }
        });
        return { type: 'FeatureCollection', features: features };
    }

    document.addEventListener('daterangechange', function (event) {
        var start = event.detail.startDate;
        var stop = event.detail.stopDate;
        if (start === null) {
            // "All time": show every incident
            rangeStart = null;
            rangeEnd = null;
        } else {
            rangeStart = start.getTime();
            // stopDate is inclusive, so include everything up to the start of the following day
            rangeEnd = new Date(stop.getFullYear(), stop.getMonth(), stop.getDate() + 1).getTime();
        }
        var source = map.getSource('incidents');
        if (source) {
            source.setData(heatmapData());
            updatePins(true);
        }
    });

    function incidentHeatmap() {
        map.addSource('incidents', {
            type: 'geojson',
            data: heatmapData()
        });

        // Each incident raises the heat at its spot; overlapping incidents stack.
        // A cluster of many incidents shows up as a larger red area rather than just a deeper color.
        map.addLayer({
            id: 'incidents-heat',
            type: 'heatmap',
            source: 'incidents',
            paint: {
                'heatmap-weight': 1,

                // Keep isolated incidents from becoming red too easily.
                'heatmap-intensity': 0.6,

                // Blend nearby incidents into smoother regions.
                'heatmap-radius': [
                    'interpolate', ['linear'], ['zoom'],
                    8, 20,
                    11, 40,
                    15, 75,
                    18, 100
                ],

                'heatmap-opacity': 0.75,

                // Transparent -> green -> yellow -> orange -> red.
                'heatmap-color': [
                    'interpolate', ['linear'], ['heatmap-density'],
                    0,    'rgba(46, 160, 67, 0)',
                    0.08, 'rgba(46, 160, 67, 0.35)',
                    0.25, '#66bd63',
                    0.50, '#fee08b',
                    0.75, '#f46d43',
                    1,    '#d73027'
                ]
            }
        });
    }

    map.on('error', function (event) {
        if (!map.isStyleLoaded()) {
            showStatus('Map style could not be loaded from ' + (styleUrl || 'the development tile source') + '.');
        }
        console.error(event.error);
    });

    // Switch the dev basemap between muted (pressed) and its regular colors
    var colorToggle = document.getElementById('toggle-map-color');
    function setMuted(muted) {
        if (muted) {
            map.setPaintProperty('osm', 'raster-saturation', -0.85);
            map.setPaintProperty('osm', 'raster-contrast', -0.15);
            colorToggle.textContent = 'Show regular map';
        } else {
            map.setPaintProperty('osm', 'raster-saturation', 0);
            map.setPaintProperty('osm', 'raster-contrast', 0);
            colorToggle.textContent = 'Show muted map';
        }
        colorToggle.setAttribute('aria-pressed', String(muted));
    }
    colorToggle.addEventListener('click', function () {
        if (!map.isStyleLoaded() || !map.getLayer('osm')) {
            return;
        }
        setMuted(colorToggle.getAttribute('aria-pressed') !== 'true');
    });

    map.on('load', function () {
        incidentHeatmap();
        updatePins();
        // Only the dev basemap has the 'osm' layer to recolor
        colorToggle.hidden = !map.getLayer('osm');
        if (styleUrl) {
            showStatus('');
        } else {
            showStatus('Development tiles');
        }
    });
})();
