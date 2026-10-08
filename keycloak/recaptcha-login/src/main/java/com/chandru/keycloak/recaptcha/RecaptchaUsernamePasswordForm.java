package com.chandru.keycloak.recaptcha;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.authenticators.browser.UsernamePasswordForm;
import org.keycloak.services.messages.Messages;

/**
 * Keycloak's standard username/password login form, plus a server-side
 * Google reCAPTCHA v2 check before the password is even looked at.
 *
 * The checkbox itself is added to the page by the "bank" login theme
 * (js/login-recaptcha.js). Google puts the token in the form field
 * "g-recaptcha-response", which is verified here. Without a valid token
 * the login form is shown again with an error, so the check cannot be
 * skipped by posting the form directly.
 *
 * When no keys are configured it behaves exactly like the normal form.
 */
public class RecaptchaUsernamePasswordForm extends UsernamePasswordForm {

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();

        if (!formData.containsKey("cancel") && Recaptcha.enabled()
                && !Recaptcha.verify(formData.getFirst(Recaptcha.RESPONSE_FIELD))) {
            Response challenge = context.form()
                    .setError(Messages.RECAPTCHA_FAILED)
                    .createLoginUsernamePassword();
            context.challenge(challenge);
            return;
        }
        super.action(context);
    }
}
