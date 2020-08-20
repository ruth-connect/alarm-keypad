package uk.me.ruthmills.alarmkeypad.service;

import static uk.me.ruthmills.alarmkeypad.model.AlarmState.ARMED_AWAY;
import static uk.me.ruthmills.alarmkeypad.model.AlarmState.ARMED_HOME;
import static uk.me.ruthmills.alarmkeypad.model.AlarmState.ARMED_NIGHT;
import static uk.me.ruthmills.alarmkeypad.model.AlarmState.COUNTDOWN;
import static uk.me.ruthmills.alarmkeypad.model.AlarmState.DISARMED;
import static uk.me.ruthmills.alarmkeypad.model.AlarmState.TRIGGERED;
import static uk.me.ruthmills.alarmkeypad.model.AlarmState.UNKNOWN;

import java.util.Date;

import javax.annotation.PostConstruct;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import uk.me.ruthmills.alarmkeypad.model.AlarmState;

@Service
public class AlarmStateServiceImpl implements AlarmStateService {

	private static final long STATE_CHANGE_TIMEOUT = 5000L;
	private static final long KEY_PRESS_TIMEOUT = 5000L;

	@Autowired
	private BuzzerService buzzerService;

	@Autowired
	private LedService ledService;

	@Value("${endpoint}")
	private String endpoint;

	@Value("${token}")
	private String token;

	private volatile AlarmState alarmState;
	private volatile StringBuilder code;
	private volatile Date lastKeyPressTime;
	private volatile Date lastStateChangeTime;
	private volatile int ledCount;
	private volatile RestTemplate restTemplate;

	@PostConstruct
	public void initialise() {
		alarmState = UNKNOWN;
		code = new StringBuilder();
		restTemplate = new RestTemplate(getClientHttpRequestFactory());
	}

	private ClientHttpRequestFactory getClientHttpRequestFactory() {
		int timeout = 9000;
		RequestConfig config = RequestConfig.custom().setConnectTimeout(timeout).setConnectionRequestTimeout(timeout)
				.setSocketTimeout(timeout).build();
		CloseableHttpClient client = HttpClientBuilder.create().setDefaultRequestConfig(config).build();
		return new HttpComponentsClientHttpRequestFactory(client);
	}

	@Override
	public void armedAway() {
		alarmState = ARMED_AWAY;
		lastStateChangeTime = new Date();
	}

	@Override
	public void armedNight() {
		alarmState = ARMED_NIGHT;
		lastStateChangeTime = new Date();
	}

	@Override
	public void armedHome() {
		alarmState = ARMED_HOME;
		lastStateChangeTime = new Date();
	}

	@Override
	public void disarmed() {
		alarmState = DISARMED;
		lastStateChangeTime = new Date();
	}

	@Override
	public void countdown() {
		alarmState = COUNTDOWN;
		lastStateChangeTime = new Date();
	}

	@Override
	public void triggered() {
		alarmState = TRIGGERED;
		lastStateChangeTime = new Date();
	}

	@Override
	public void keyPressed(char key) {
		if (key >= '0' && key <= '9') {
			handleCodeNumber(key);
		} else if (key >= 'A' && key <= 'D') {
			handleCommand(key);
		} else if (key == '*') {
			handleDelete();
		} else if (key == '#') {
			handleShowStatus();
		}
	}

	private void handleCodeNumber(char key) {
		lastKeyPressTime = new Date();
		code.append(key);
		ledService.setLeds(code.length() % 4 >= 1, code.length() % 4 >= 2, code.length() % 4 >= 3,
				code.length() > 0 && code.length() % 4 == 0);
		ledCount = 0;
		beep(100);
	}

	private void handleCommand(char key) {
		if (code.length() > 0) {
			beep(200);
			StringBuilder requestJson = new StringBuilder();
			requestJson.append("{\"state\": {\"command\": \"");
			requestJson.append(getCommand(key));
			requestJson.append("\", \"code\": \"");
			requestJson.append(code);
			requestJson.append("\"}}");

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.set("Authorization", "Bearer " + token);

			restTemplate.postForEntity(endpoint, new HttpEntity<String>(requestJson.toString(), headers), String.class);

			restTemplate.delete(endpoint, new HttpEntity<String>("", headers));

			lastKeyPressTime = null;
			clearCode();
		}
	}

	private String getCommand(char key) {
		switch (key) {
		case 'A':
			return "armed_away";
		case 'B':
			return "armed_night";
		case 'C':
			return "armed_home";
		default:
			return "disarmed";
		}
	}

	private void handleDelete() {
		if (code.length() > 0) {
			lastKeyPressTime = new Date();
			code.delete(code.length() - 1, code.length());
			ledService.setLeds(code.length() % 4 == 1, code.length() % 4 == 2, code.length() % 4 == 3,
					code.length() > 0 && code.length() % 4 == 0);
			ledCount = 0;
			beep(100);
		}
	}

	private void handleShowStatus() {
		clearCode();
		lastKeyPressTime = null;
		lastStateChangeTime = new Date();
		ledService.setLeds(alarmState.equals(ARMED_AWAY), alarmState.equals(ARMED_NIGHT), alarmState.equals(ARMED_HOME),
				alarmState.equals(DISARMED));
		ledCount = 0;
		beep(200);
	}

	private void clearCode() {
		if (code.length() > 0) {
			code.delete(0, code.length());
		}
	}

	@Override
	public void tick() {
		if (lastKeyPressTime == null || new Date().getTime() - lastKeyPressTime.getTime() > KEY_PRESS_TIMEOUT) {
			clearCode();
			if (alarmState.equals(TRIGGERED)) {
				flashTriggered();
			} else if (alarmState.equals(COUNTDOWN)) {
				flashCountdown();
			} else if (lastStateChangeTime != null
					&& new Date().getTime() - lastStateChangeTime.getTime() < STATE_CHANGE_TIMEOUT) {
				flashState();
			} else {
				flashNormal();
			}
		}
	}

	private void flashTriggered() {
		flash(250, true, true, true, true);
		flash(250, false, false, false, false);
		flash(250, true, true, true, true);
		flash(0, false, false, false, false);
		ledCount = 0;
	}

	private void flashCountdown() {
		flash(250, false, false, false, true);
		flash(250, false, false, true, false);
		flash(250, false, true, false, false);
		flash(0, true, false, false, false);
		ledCount = 0;
	}

	private void flashState() {
		flash(250, alarmState.equals(ARMED_AWAY), alarmState.equals(ARMED_NIGHT), alarmState.equals(ARMED_HOME),
				alarmState.equals(DISARMED));
		flash(250, false, false, false, false);
		flash(250, alarmState.equals(ARMED_AWAY), alarmState.equals(ARMED_NIGHT), alarmState.equals(ARMED_HOME),
				alarmState.equals(DISARMED));
		flash(250, false, false, false, false);
		ledCount = 0;
	}

	private void flashNormal() {
		flash(500, ledCount == 0, ledCount == 1, ledCount == 2, ledCount == 3);
		incrementLedCount();
		flash(0, ledCount == 0, ledCount == 1, ledCount == 2, ledCount == 3);
		incrementLedCount();
	}

	private void incrementLedCount() {
		ledCount++;
		if (ledCount > 3) {
			ledCount = 0;
		}
	}

	@Override
	public void sleep(int milliseconds) {
		if (milliseconds > 0) {
			try {
				Thread.sleep(milliseconds);
			} catch (InterruptedException ex) {
			}
		}
	}

	private void beep(int milliseconds) {
		buzzerService.setBuzzer(true);
		sleep(milliseconds);
		buzzerService.setBuzzer(false);
	}

	private void flash(int milliseconds, boolean red, boolean amber, boolean green, boolean blue) {
		if (lastKeyPressTime == null || new Date().getTime() - lastKeyPressTime.getTime() > KEY_PRESS_TIMEOUT) {
			ledService.setLeds(red, amber, green, blue);
			sleep(milliseconds);
		}
	}
}
