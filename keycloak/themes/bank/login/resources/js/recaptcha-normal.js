/**
 * Change reCAPTCHA from compact to normal (full checkbox) size.
 * Runs before grecaptcha.render() so the widget renders at full size.
 */
(function () {
    // Change data-size on existing div immediately
    function fixSize() {
        var divs = document.querySelectorAll('.g-recaptcha');
        for (var i = 0; i < divs.length; i++) {
            divs[i].setAttribute('data-size', 'normal');
        }
    }

    // Run immediately if DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', fixSize);
    } else {
        fixSize();
    }

    // Also override grecaptcha.render to force normal size when it's called
    var _readyInterval = setInterval(function () {
        if (window.grecaptcha && window.grecaptcha.render) {
            clearInterval(_readyInterval);
            var _origRender = window.grecaptcha.render;
            window.grecaptcha.render = function (container, params) {
                if (params) {
                    params.size = 'normal';
                } else {
                    params = { size: 'normal' };
                }
                return _origRender(container, params);
            };
            // Re-render any already-rendered compact widgets
            fixSize();
        }
    }, 50);
})();
