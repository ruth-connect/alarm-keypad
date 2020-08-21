package uk.me.ruthmills.alarmkeypad.service;

import static uk.me.ruthmills.alarmkeypad.model.AlarmState.ARMED_AWAY;
import static uk.me.ruthmills.alarmkeypad.model.AlarmState.ARMED_HOME;
import static uk.me.ruthmills.alarmkeypad.model.AlarmState.ARMED_NIGHT;
import static uk.me.ruthmills.alarmkeypad.model.AlarmState.COUNTDOWN;
import static uk.me.ruthmills.alarmkeypad.model.AlarmState.DISARMED;
import static uk.me.ruthmills.alarmkeypad.model.AlarmState.TRIGGERED;
import static uk.me.ruthmills.alarmkeypad.model.AlarmState.UNKNOWN;

import java.time.LocalDateTime;
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

	private static final long STATE_CHANGE_TIMEOUT = 10000L;
	private static final long KEY_PRESS_TIMEOUT = 5000L;
	private static final long EXIT_WARNING_TIMEOUT = 50000L;
	private static final long EXIT_TIMEOUT = 60000L;

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
	private volatile Date requestedExitTime;
	private volatile AlarmState requestedExitState;
	private volatile RestTemplate restTemplate;

	@PostConstruct
	public void initialise() {
		alarmState = UNKNOWN;
		code = new StringBuilder();
		restTemplate = new RestTemplate(getClientHttpRequestFactory());
		sendCommand("initialise");
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
		cancelExit();
	}

	@Override
	public void armedNight() {
		alarmState = ARMED_NIGHT;
		lastStateChangeTime = new Date();
		cancelExit();
	}

	@Override
	public void armedHome() {
		alarmState = ARMED_HOME;
		lastStateChangeTime = new Date();
		cancelExit();
	}

	@Override
	public void disarmed() {
		alarmState = DISARMED;
		lastStateChangeTime = new Date();
		cancelExit();
	}

	@Override
	public void countdown() {
		alarmState = COUNTDOWN;
		lastStateChangeTime = new Date();
		cancelExit();
	}

	@Override
	public void triggered() {
		alarmState = TRIGGERED;
		lastStateChangeTime = new Date();
		cancelExit();
	}

	@Override
	public void keyPressed(char key) {
		cancelExit();
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
		showCodeLength();
	}

	private void handleCommand(char key) {
		if (code.length() > 0) {
			beep(200);
			if (getCommand(key).equals("armed_away") || getCommand(key).equals("armed_night")) {
				requestedExitState = getState(key);
				requestedExitTime = new Date();
			} else {
				sendCommand(getCommand(key));
			}
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

	private String getCommand(AlarmState alarmState) {
		switch (alarmState) {
		case ARMED_AWAY:
			return "armed_away";
		case ARMED_NIGHT:
			return "armed_night";
		case ARMED_HOME:
			return "armed_home";
		default:
			return "disarmed";
		}
	}

	private AlarmState getState(char key) {
		switch (key) {
		case 'A':
			return ARMED_AWAY;
		case 'B':
			return ARMED_NIGHT;
		case 'C':
			return ARMED_HOME;
		default:
			return DISARMED;
		}
	}

	private void sendCommand(String command) {
		StringBuilder requestJson = new StringBuilder();
		requestJson.append("{\"state\": {\"command\": \"");
		requestJson.append(command);
		requestJson.append("\", \"code\": \"");
		requestJson.append(code);
		requestJson.append("\"}}");

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("Authorization", "Bearer " + token);

		restTemplate.postForEntity(endpoint, new HttpEntity<String>(requestJson.toString(), headers), String.class);

		restTemplate.delete(endpoint, new HttpEntity<String>("", headers));
	}

	private void handleDelete() {
		if (code.length() > 0) {
			lastKeyPressTime = new Date();
			code.delete(code.length() - 1, code.length());
			showCodeLength();
			beep(100);
		}
	}

	private void handleShowStatus() {
		clearCode();
		lastKeyPressTime = null;
		lastStateChangeTime = new Date();
		ledService.setLeds(alarmState.equals(ARMED_AWAY), alarmState.equals(ARMED_NIGHT), alarmState.equals(ARMED_HOME),
				alarmState.equals(DISARMED));
		beep(200);
	}

	private void showCodeLength() {
		ledService.setLeds(code.length() % 8 >= 5 ^ code.length() % 4 == 1,
				code.length() % 8 >= 5 ^ code.length() % 4 == 2, code.length() % 8 >= 5 ^ code.length() % 4 == 3,
				code.length() % 8 >= 5 ^ code.length() > 0 && code.length() % 4 == 0);
	}

	private void clearCode() {
		if (code.length() > 0) {
			code.delete(0, code.length());
		}
	}

	@Override
	public void tick() {
		if (!keyPressed()) {
			clearCode();
			if (alarmState.equals(TRIGGERED)) {
				flashTriggered();
			} else if (alarmState.equals(COUNTDOWN)) {
				flashCountdown();
			} else if (exitRequested()) {
				if (beforeExitWarningTime()) {
					flashExit();
				} else if (beforeExitTime()) {
					flashExitWarning();
				} else {
					requestedExitTime = null;
					sendCommand(getCommand(requestedExitState));
					cancelExit();
				}
			}
		} else if (stateChanged()) {
			flashState();
		} else {
			flashNormal();
		}
	}

	private void flashTriggered() {
		flash(250, true, true, true, true);
		flash(250, false, false, false, false);
		flash(250, true, true, true, true);
		flash(0, false, false, false, false);
	}

	private void flashCountdown() {
		flash(250, false, false, false, true);
		flash(250, false, false, true, false);
		flash(250, false, true, false, false);
		flash(0, true, false, false, false);
	}

	private void flashState() {
		setLedForState(alarmState);
		sleep(250);
		flash(250, false, false, false, false);
		setLedForState(alarmState);
		sleep(250);
		flash(0, false, false, false, false);
	}

	private void flashExit() {
		if (LocalDateTime.now().getSecond() % 2 == 0) {
			if (!keyPressed()) {
				setLedForState(requestedExitState);
				beep(250);
			}
			flash(250, false, false, false, false);
			if (!keyPressed()) {
				setLedForState(requestedExitState);
				sleep(250);
			}
			flash(0, false, false, false, false);
		} else {
			if (!keyPressed()) {
				ledService.setLeds(true, false, false, false);
				beep(250);
			}
			flash(250, false, true, false, false);
			flash(250, false, false, true, false);
			flash(0, false, false, false, true);
		}
	}

	private void flashExitWarning() {
		if (LocalDateTime.now().getSecond() % 2 == 0) {
			if (!keyPressed()) {
				setLedForState(requestedExitState);
				beep(250);
			}
			flash(250, false, false, false, false);
			if (!keyPressed()) {
				setLedForState(requestedExitState);
				beep(250);
			}
			flash(0, false, false, false, false);
		} else {
			if (!keyPressed()) {
				ledService.setLeds(true, false, false, false);
				beep(250);
			}
			flash(250, false, true, false, false);
			if (!keyPressed()) {
				ledService.setLeds(false, false, true, false);
				beep(250);
			}
			flash(0, false, false, false, true);
		}
	}

	private void flashNormal() {
		if (LocalDateTime.now().getSecond() % 4 == 0) {
			flash(250, true, false, false, false);
			flash(0, false, false, false, false);
		}
	}

	private void cancelExit() {
		requestedExitTime = null;
		requestedExitState = null;
	}

	private void setLedForState(AlarmState state) {
		ledService.setLeds(state.equals(ARMED_AWAY), state.equals(ARMED_NIGHT), state.equals(ARMED_HOME),
				state.equals(DISARMED));
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
		if (!keyPressed()) {
			ledService.setLeds(red, amber, green, blue);
			sleep(milliseconds);
		}
	}

	private boolean keyPressed() {
		return lastKeyPressTime != null && new Date().getTime() - lastKeyPressTime.getTime() < KEY_PRESS_TIMEOUT;
	}

	private boolean stateChanged() {
		return lastStateChangeTime != null
				&& new Date().getTime() - lastStateChangeTime.getTime() < STATE_CHANGE_TIMEOUT;
	}

	private boolean exitRequested() {
		return requestedExitTime != null && requestedExitState != null;
	}

	private boolean beforeExitWarningTime() {
		return new Date().getTime() - lastKeyPressTime.getTime() < EXIT_WARNING_TIMEOUT;
	}

	private boolean beforeExitTime() {
		return new Date().getTime() - lastKeyPressTime.getTime() < EXIT_TIMEOUT;
	}
}
