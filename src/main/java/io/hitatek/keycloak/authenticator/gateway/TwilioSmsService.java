package io.hitatek.keycloak.authenticator.gateway;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class TwilioSmsService implements SmsService {
	private static final String ACCOUNT_SID = "ACCOUNT_SID";
	private static final String AUTH_TOKEN = "AUTH_TOKEN";
	private static final String PHONE_NUMBER = "PHONE_NUMBER_FROM";

	TwilioSmsService(Map<String, String> config) {
		//TODO
	}

	@Override
	public void send(String phoneNumber, String message) {
		try {
			HttpPost post = new HttpPost("https://api.twilio.com/2010-04-01/Accounts/"+ACCOUNT_SID+"/Messages.json");
			String userCredentials = ACCOUNT_SID+":"+AUTH_TOKEN;
			String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userCredentials.getBytes()));
			post.setHeader("Authorization",basicAuth);
			List<NameValuePair> urlParameters = new ArrayList<>();
			urlParameters.add(new BasicNameValuePair("Body", message));
			urlParameters.add(new BasicNameValuePair("From", PHONE_NUMBER));
			urlParameters.add(new BasicNameValuePair("To", phoneNumber));

			post.setEntity(new UrlEncodedFormEntity(urlParameters,"utf-8"));

			try (CloseableHttpClient httpClient = HttpClients.createDefault();
				 CloseableHttpResponse response = httpClient.execute(post)) {

				System.out.println(EntityUtils.toString(response.getEntity()));
			}

		} catch (Exception e) {
			System.out.println("Send SMS exception in NetClientGet:- " + e.getMessage());
		}
	}
}
