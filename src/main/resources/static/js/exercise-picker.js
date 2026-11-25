/*
 * Gyakorlatválasztó ablak az edzés és a sablon űrlapjához (UC7, UC15).
 *
 * A gyakorlatok kártyákon jelennek meg: kép (ha van, különben a kategória ikonja), név, kategória és elsődleges
 * izomcsoport. Felül kereső (név vagy izomcsoport, ékezettől függetlenül: „fekveny” → Fekvenyomás) és szűrők:
 * kategória, izomcsoport, csak a saját gyakorlatok. Kattintásra (vagy a keresőben Enterre az első találatra) a
 * gyakorlat bekerül az űrlapba, és az ablak bezárul.
 *
 * Használat: ExercisePicker.attach(megnyitóGomb, katalógus, function (gyakorlatAzonosító) { ... });
 * A katalógus elemei: id, name, category, categoryLabel, muscle, muscleLabel, image, shared (ExerciseCatalog).
 */
(function () {
    'use strict';

    var ICONS = { WEIGHTS: 'bi-lightning-charge-fill', CARDIO: 'bi-heart-pulse', BODYWEIGHT: 'bi-person-arms-up' };
    var CATEGORY_ORDER = ['WEIGHTS', 'BODYWEIGHT', 'CARDIO'];
    var counter = 0;

    function el(tag, props, children) {
        var node = document.createElement(tag);
        Object.keys(props || {}).forEach(function (k) {
            if (k === 'class') { node.className = props[k]; }
            else if (k === 'text') { node.textContent = props[k]; }
            else { node.setAttribute(k, props[k]); }
        });
        (children || []).forEach(function (c) { if (c) { node.appendChild(c); } });
        return node;
    }

    /** Kisbetűs, ékezet nélküli alak a kereséshez. */
    function plain(text) {
        return (text || '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
    }

    function modalApi(modal) {
        return window.bootstrap && window.bootstrap.Modal ? window.bootstrap.Modal.getOrCreateInstance(modal) : null;
    }

    function attach(openButton, catalog, onPick) {
        var id = 'exercise-picker-' + (++counter);
        var state = { query: '', category: '', muscle: '', ownOnly: false };
        var modal, search, grid, empty, count, picked = false;

        function chip(label, active, onClick) {
            var button = el('button', { type: 'button', 'class': 'btn btn-sm btn-outline-secondary picker-chip' + (active ? ' active' : ''),
                'aria-pressed': String(active), text: label });
            button.addEventListener('click', onClick);
            return button;
        }

        function categoryChips(box) {
            box.innerHTML = '';
            var present = CATEGORY_ORDER.filter(function (c) { return catalog.some(function (e) { return e.category === c; }); });
            box.appendChild(chip('Mind', state.category === '', function () { state.category = ''; refresh(box); }));
            present.forEach(function (c) {
                var label = catalog.filter(function (e) { return e.category === c; })[0].categoryLabel;
                box.appendChild(chip(label, state.category === c, function () { state.category = c; refresh(box); }));
            });
        }

        function refresh(chipBox) {
            categoryChips(chipBox);
            render();
        }

        function matches(e) {
            if (state.category && e.category !== state.category) { return false; }
            if (state.muscle && e.muscle !== state.muscle) { return false; }
            if (state.ownOnly && e.shared) { return false; }
            var q = plain(state.query).trim();
            return !q || plain(e.name).indexOf(q) >= 0 || plain(e.muscleLabel).indexOf(q) >= 0;
        }

        function card(e) {
            var picture = e.image
                ? el('img', { 'class': 'picker-img', src: e.image, alt: '', loading: 'lazy' })
                : el('div', { 'class': 'picker-img picker-icon picker-icon-' + e.category }, [
                    el('i', { 'class': 'bi ' + (ICONS[e.category] || 'bi-collection'), 'aria-hidden': 'true' })]);
            var button = el('button', { type: 'button', 'class': 'picker-card', 'data-id': e.id }, [
                picture,
                el('span', { 'class': 'picker-name', text: e.name }),
                el('span', { 'class': 'picker-meta' }, [
                    el('span', { 'class': 'cat-badge cat-' + e.category, text: e.categoryLabel }),
                    e.muscleLabel ? el('span', { text: e.muscleLabel }) : null,
                    e.shared ? null : el('span', { 'class': 'badge rounded-pill bg-secondary-subtle text-secondary-emphasis', text: 'saját' })
                ])
            ]);
            button.addEventListener('click', function () { pick(e.id); });
            return el('div', { 'class': 'col-6 col-md-4 col-lg-3' }, [button]);
        }

        function render() {
            grid.innerHTML = '';
            var shown = catalog.filter(matches);
            shown.forEach(function (e) { grid.appendChild(card(e)); });
            empty.hidden = shown.length > 0;
            count.textContent = shown.length + ' gyakorlat';
        }

        function build() {
            var chipBox = el('div', { 'class': 'd-flex flex-wrap gap-1', role: 'group', 'aria-label': 'Kategória' });
            search = el('input', { 'class': 'form-control', type: 'search', id: id + '-search', autocomplete: 'off',
                placeholder: 'Keresés név vagy izomcsoport szerint' });
            search.addEventListener('input', function () { state.query = search.value; render(); });
            search.addEventListener('keydown', function (ev) {
                if (ev.key === 'Enter') {
                    ev.preventDefault();
                    var first = grid.querySelector('.picker-card');
                    if (first) { pick(first.getAttribute('data-id')); }
                }
            });

            var muscles = {};
            catalog.forEach(function (e) { if (e.muscle) { muscles[e.muscle] = e.muscleLabel; } });
            var muscleSelect = el('select', { 'class': 'form-select', id: id + '-muscle', 'aria-label': 'Izomcsoport' },
                [el('option', { value: '', text: 'Minden izomcsoport' })].concat(Object.keys(muscles)
                    .sort(function (a, b) { return muscles[a].localeCompare(muscles[b], 'hu'); })
                    .map(function (m) { return el('option', { value: m, text: muscles[m] }); })));
            muscleSelect.addEventListener('change', function () { state.muscle = muscleSelect.value; render(); });

            var ownBox = null;
            if (catalog.some(function (e) { return !e.shared; })) {
                var own = el('input', { 'class': 'form-check-input', type: 'checkbox', id: id + '-own' });
                own.addEventListener('change', function () { state.ownOnly = own.checked; render(); });
                ownBox = el('div', { 'class': 'form-check form-switch mb-0' }, [own,
                    el('label', { 'class': 'form-check-label', 'for': id + '-own', text: 'Csak a saját gyakorlataim' })]);
            }

            grid = el('div', { 'class': 'row g-2 picker-grid' });
            empty = el('p', { 'class': 'text-body-secondary text-center py-4 mb-0',
                text: 'Nincs a keresésnek megfelelő gyakorlat. Módosítsd a keresést vagy a szűrőket.' });
            count = el('span', { 'class': 'small text-body-secondary', 'aria-live': 'polite' });

            modal = el('div', { 'class': 'modal fade exercise-picker', id: id, tabindex: '-1', 'aria-labelledby': id + '-title',
                'aria-hidden': 'true' }, [
                el('div', { 'class': 'modal-dialog modal-xl modal-dialog-scrollable modal-fullscreen-md-down' }, [
                    el('div', { 'class': 'modal-content' }, [
                        el('div', { 'class': 'modal-header' }, [
                            el('h2', { 'class': 'modal-title h5', id: id + '-title', text: 'Gyakorlat hozzáadása' }),
                            el('button', { type: 'button', 'class': 'btn-close', 'data-bs-dismiss': 'modal', 'aria-label': 'Bezárás' })
                        ]),
                        el('div', { 'class': 'modal-body pt-3' }, [
                            el('div', { 'class': 'picker-filters vstack gap-2 mb-3' }, [
                                el('label', { 'class': 'visually-hidden', 'for': id + '-search', text: 'Keresés' }),
                                search,
                                el('div', { 'class': 'd-flex flex-wrap align-items-center gap-2' }, [
                                    chipBox,
                                    el('div', { 'class': 'picker-muscle-filter' }, [muscleSelect]),
                                    ownBox,
                                    el('div', { 'class': 'ms-auto' }, [count])
                                ])
                            ]),
                            grid,
                            empty
                        ])
                    ])
                ])
            ]);
            document.body.appendChild(modal);
            modal.addEventListener('shown.bs.modal', function () { search.focus(); });
            modal.addEventListener('hidden.bs.modal', function () { if (!picked) { openButton.focus(); } });
            categoryChips(chipBox);
        }

        function open() {
            if (!modal) { build(); }
            picked = false;
            state.query = '';
            search.value = '';
            render();
            var api = modalApi(modal);
            if (api) { api.show(); } else { modal.classList.add('show'); modal.hidden = false; search.focus(); }
        }

        function pick(exerciseId) {
            picked = true;
            var api = modalApi(modal);
            if (api) { api.hide(); } else { modal.classList.remove('show'); modal.hidden = true; }
            onPick(String(exerciseId));
        }

        openButton.addEventListener('click', open);
        return { open: open };
    }

    window.ExercisePicker = { attach: attach };
})();
