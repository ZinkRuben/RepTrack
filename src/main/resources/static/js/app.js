/*
 * Témaváltás (világos / sötét / rendszer szerint). A választás a böngészőben marad (localStorage); a kezdeti
 * témát a layout <head>-jében lévő rövid szkript állítja be, hogy betöltéskor ne villanjon az oldal.
 */
(function () {
    'use strict';

    var KEY = 'reptrack-theme';
    var ICONS = { light: 'bi-sun-fill', dark: 'bi-moon-stars-fill', auto: 'bi-circle-half' };
    var media = window.matchMedia('(prefers-color-scheme: dark)');

    function stored() {
        try {
            var value = localStorage.getItem(KEY);
            return value === 'light' || value === 'dark' ? value : 'auto';
        } catch (e) {
            return 'auto';
        }
    }

    function store(mode) {
        try {
            localStorage.setItem(KEY, mode);
        } catch (e) {
            // privát mód vagy letiltott tároló: a választás csak erre az oldalbetöltésre érvényes
        }
    }

    function apply(mode) {
        var theme = mode === 'auto' ? (media.matches ? 'dark' : 'light') : mode;
        document.documentElement.setAttribute('data-bs-theme', theme);
        document.querySelectorAll('[data-theme-value]').forEach(function (button) {
            var active = button.getAttribute('data-theme-value') === mode;
            button.classList.toggle('active', active);
            button.setAttribute('aria-pressed', String(active));
        });
        document.querySelectorAll('[data-theme-icon]').forEach(function (icon) {
            icon.className = 'bi ' + ICONS[mode];
        });
    }

    apply(stored());

    media.addEventListener('change', function () {
        if (stored() === 'auto') {
            apply('auto');
        }
    });

    document.addEventListener('click', function (event) {
        var button = event.target.closest('[data-theme-value]');
        if (button) {
            var mode = button.getAttribute('data-theme-value');
            store(mode);
            apply(mode);
        }
    });
})();
