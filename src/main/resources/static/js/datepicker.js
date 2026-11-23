/*
 * Magyar dátumválasztó (flatpickr) minden dátum- és időpontmezőhöz (input type="date" és "datetime-local").
 *
 * A böngésző saját dátummezőjének formátuma a böngésző nyelvétől függ (pl. dd/mm/yyyy), ezért cseréljük le. A
 * felhasználó magyar formában látja és gépelheti az értéket (2026. 11. 23. 16:05), az űrlap viszont változatlanul
 * a szabványos formát küldi (2026-11-23T16:05), így a szerveroldalon semmi nem változik. A mező min/max, required
 * és placeholder beállításai átkerülnek. Mobilon a flatpickr a készülék saját választóját használja. Ha a könyvtár
 * nem töltődik be, a böngésző saját mezője marad.
 */
(function () {
    'use strict';

    if (!window.flatpickr) {
        return;
    }
    var hungarian = window.flatpickr.l10ns && window.flatpickr.l10ns.hu;

    document.querySelectorAll('input[type="date"], input[type="datetime-local"]').forEach(function (input) {
        var withTime = input.type === 'datetime-local';
        var label = input.id ? document.querySelector('label[for="' + input.id + '"]') : null;

        var picker = window.flatpickr(input, {
            locale: hungarian,
            enableTime: withTime,
            time_24hr: true,
            dateFormat: withTime ? 'Y-m-d\\TH:i' : 'Y-m-d',
            altInput: true,
            altFormat: withTime ? 'Y. m. d. H:i' : 'Y. m. d.',
            altInputClass: input.className,
            allowInput: true,          // gépelni is lehet (magyar formában), és a mező kiüríthető
            minDate: input.min || null,
            maxDate: input.max || null
        });

        // a látható mező kapja a címkét, hogy a címkére kattintva is megnyíljon
        var visible = picker.altInput || picker.mobileInput;
        if (visible && label) {
            visible.id = input.id + '-valaszto';
            label.htmlFor = visible.id;
        }
        if (picker.altInput && !picker.altInput.placeholder) {
            picker.altInput.placeholder = withTime ? 'éééé. hh. nn. óó:pp' : 'éééé. hh. nn.';
        }
    });
})();
