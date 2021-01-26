package io.hitatek.keycloak.authenticator;

import io.hitatek.keycloak.authenticator.gateway.SmsServiceFactory;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.email.DefaultEmailSenderProvider;
import org.keycloak.email.EmailException;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.theme.Theme;

import javax.ws.rs.core.Response;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author Niko Köbler, https://www.n-k.de, @dasniko
 */
public class SmsAuthenticator implements Authenticator {

	private static final String TPL_CODE = "login-sms.ftl";
	private static final String CODE_SENDER_EMAIL = "email";
	private static final String CODE_SENDER_SMS = "sms";

	@Override
	public void authenticate(AuthenticationFlowContext context) {
		String choosingCodeSender = context.getHttpRequest().getDecodedFormParameters().getFirst("sender");

		System.out.println("choose method to send forgot password code: " + choosingCodeSender);

		AuthenticatorConfigModel config = context.getAuthenticatorConfig();
		KeycloakSession session = context.getSession();
		UserModel user = context.getUser();

		int length = Integer.parseInt(config.getConfig().get("length"));
		int ttl = Integer.parseInt(config.getConfig().get("ttl"));

		String code = generateAuthCode(length);
		AuthenticationSessionModel authSession = context.getAuthenticationSession();
		authSession.setAuthNote("code", code);
		authSession.setAuthNote("ttl", Long.toString(System.currentTimeMillis() + (ttl * 1000)));

		try {
			Theme theme = session.theme().getTheme(Theme.Type.LOGIN);
			Locale locale = session.getContext().resolveLocale(user);
			String smsAuthText = theme.getMessages(locale).getProperty("smsAuthText");
			String smsText = String.format(smsAuthText, code, Math.floorDiv(ttl, 60));

			if(CODE_SENDER_EMAIL.equals(choosingCodeSender)){
				sendCodeThroughEmail(session, user, smsText);
			}else if(CODE_SENDER_SMS.equals(choosingCodeSender)){
				sendCodeThroughSMS(config, user, smsText);
			}

			context.challenge(context.form().setAttribute("realm", context.getRealm()).createForm(TPL_CODE));
		} catch (Exception e) {
			context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR,
				context.form().setError("smsAuthSmsNotSent", e.getMessage())
					.createErrorPage(Response.Status.INTERNAL_SERVER_ERROR));
		}
	}

	private void sendCodeThroughSMS(AuthenticatorConfigModel config, UserModel user, String smsText) {
		String mobileNumber = user.getFirstAttribute("mobile_number");
		if(mobileNumber != null && !mobileNumber.isEmpty()) {
			mobileNumber = mobileNumber.replaceFirst("0", "+84");
			SmsServiceFactory.get(config.getConfig()).send(mobileNumber, smsText);
		}
	}

	private void sendCodeThroughEmail(KeycloakSession session, UserModel user, String smsText) throws EmailException {
		String email = user.getEmail();
		if(email != null && !email.isEmpty()) {
			DefaultEmailSenderProvider senderProvider = new DefaultEmailSenderProvider(session);
			senderProvider.send(
				session.getContext().getRealm().getSmtpConfig(),
				user,
				"Reset Password Code",
				smsText,
				smsText
			);
		}
	}

	@Override
	public void action(AuthenticationFlowContext context) {
		String enteredCode = context.getHttpRequest().getDecodedFormParameters().getFirst("code");

		AuthenticationSessionModel authSession = context.getAuthenticationSession();
		String code = authSession.getAuthNote("code");
		String ttl = authSession.getAuthNote("ttl");

		if (code == null || ttl == null) {
			context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR,
				context.form().createErrorPage(Response.Status.INTERNAL_SERVER_ERROR));
			return;
		}

		boolean isValid = enteredCode.equals(code);
		if (isValid) {
			if (Long.parseLong(ttl) < System.currentTimeMillis()) {
				// expired
				context.failureChallenge(AuthenticationFlowError.EXPIRED_CODE,
					context.form().setError("smsAuthCodeExpired").createErrorPage(Response.Status.BAD_REQUEST));
			} else {
				// valid
				context.success();
			}
		} else {
			// invalid
			AuthenticationExecutionModel execution = context.getExecution();
			if (execution.isRequired()) {
				context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
					context.form().setAttribute("realm", context.getRealm())
						.setError("smsAuthCodeInvalid").createForm(TPL_CODE));
			} else if (execution.isConditional() || execution.isAlternative()) {
				context.attempted();
			}
		}
	}

	@Override
	public boolean requiresUser() {
		return true;
	}

	@Override
	public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
		try{
			return user.getFirstAttribute("mobile_number") != null;
		}catch (Exception e){
			System.out.println(e.getMessage());
			return false;
		}
	}

	@Override
	public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
	}

	@Override
	public void close() {
	}

	private String generateAuthCode(int length) {
		double maxValue = Math.pow(10.0, length);
		int randomNumber = ThreadLocalRandom.current().nextInt((int) maxValue);
		return String.format("%0" + length + "d", randomNumber);
	}

}
