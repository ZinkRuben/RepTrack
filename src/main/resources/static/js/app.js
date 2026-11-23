/*
 * Témaváltás egyetlen gombbal. Alapból az oldal a rendszer (operációs rendszer, böngésző) témáját követi, akkor is,
 * ha az közben megváltozik. A gomb a másik témára vált, és a választást a böngésző megjegyzi (localStorage). Ha a
 * felhasználó visszavált arra, amit a rendszer is használ, a megjegyzett választás törlődik, és az oldal ismét a
 * rendszert követi. A kezdeti témát a layout <head>-jében lévő rövid szkript állítja be, hogy betöltéskor ne
 * villanjon az oldal.
 */
(function () {
    'use strict';

    var KEY = 'reptrack-theme';
    var media = window.matchMedia('(prefers-color-scheme: dark)');
    var sessionChoice = null;   // ha a böngésző nem enged tárolni (pl. privát mód), a választás az oldalon marad

    function system() {
        return media.matches ? 'dark' : 'light';
    }

    function stored() {
        try {
            var value = localStorage.getItem(KEY);
            return value === 'light' || value === 'dark' ? value : null;
        } catch (e) {
            return sessionChoice;
        }
    }

    function remember(theme) {
        sessionChoice = theme === system() ? null : theme;
        try {
            if (sessionChoice === null) {
                localStorage.removeItem(KEY);
            } else {
                localStorage.setItem(KEY, sessionChoice);
            }
        } catch (e) {
            // a tároló nem érhető el: a sessionChoice érvényes erre az oldalbetöltésre
        }
    }

    function current() {
        return stored() || system();
    }

    function apply() {
        var theme = current();
        var next = theme === 'dark' ? 'light' : 'dark';
        document.documentElement.setAttribute('data-bs-theme', theme);
        document.querySelectorAll('[data-theme-toggle]').forEach(function (button) {
            // az ikon és a felirat azt mutatja, mire vált a gomb
            button.setAttribute('aria-label', next === 'dark' ? 'Váltás sötét témára' : 'Váltás világos témára');
            button.setAttribute('title', next === 'dark' ? 'Sötét téma' : 'Világos téma');
            var icon = button.querySelector('[data-theme-icon]');
            if (icon) {
                icon.className = 'bi ' + (next === 'dark' ? 'bi-moon-stars-fill' : 'bi-sun-fill');
            }
        });
    }

    apply();

    // a rendszer témájának változását követi, hacsak a felhasználó nem választott mást
    media.addEventListener('change', apply);

    document.addEventListener('click', function (event) {
        if (event.target.closest('[data-theme-toggle]')) {
            remember(current() === 'dark' ? 'light' : 'dark');
            apply();
        }
    });
})();
