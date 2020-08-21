package uk.me.ruthmills.alarmkeypad.service.impl;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import uk.me.ruthmills.alarmkeypad.service.AlarmStateService;
import uk.me.ruthmills.alarmkeypad.service.BuzzerService;
import uk.me.ruthmills.alarmkeypad.service.LedService;

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

	private RestTemplate restTemplate;
	private final Logger logger = LoggerFactory.getLogger(AlarmStateServiceImpl.class);

	@PostConstruct
	public void initialise() {
		alarmState = UNKNOWN;
		code = new StringBuilder();
		restTemplate = new RestTemplate(getClientHttpRequestFactory());
		sendCommand("initialise");
		logger.info("Alarm State set to initialised");
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
		logger.info("Alarm State set to armed_away");
	}

	@Override
	public void armedNight() {
		alarmState = ARMED_NIGHT;
		lastStateChangeTime = new Date();
		cancelExit();
		logger.info("Alarm State set to armed_night");
	}

	@Override
	public void armedHome() {
		alarmState = ARMED_HOME;
		lastStateChangeTime = new Date();
		cancelExit();
		logger.info("Alarm State set to armed_home");
	}

	@Override
	public void disarmed() {
		alarmState = DISARMED;
		lastStateChangeTime = new Date();
		cancelExit();
		logger.info("Alarm State set to disarmed");
	}

	@Override
	public void countdown() {
		alarmState = COUNTDOWN;
		lastStateChangeTime = new Date();
		cancelExit();
		logger.info("Alarm State set to countdown");
	}

	@Override
	public void triggered() {
		alarmState = TRIGGERED;
		lastStateChangeTime = new Date();
		cancelExit();
		logger.info("Alarm State set to triggered");
	}

	@Override
	public void invalidCode() {
		requestedExitTime = null;
		requestedExitState = null;
		logger.info("Invalid Code entered");
	}

	@Override
	public void keyPressed(char key) {
		cancelExit();
		logger.info("Key pressed: " + key);
		if (key >= '0' && key <= '9') {
			handleCodeNumber(key);
		} else if (key >= 'A' && key <= 'D') {
			handleCommand(key);
		} else if (key == '*') {
			handleDelete();
		} else if (key == '#') {
			handleShowState();
		}
	}

	private void handleCodeNumber(char key) {
		lastKeyPressTime = new Date();
		code.append(key);
		logger.info("Code entered: " + code.toString());
		showCodeLength();
		beep(100);
	}

	private void handleCommand(char key) {
		if (code.length() > 0) {
			if (!alarmState.equals(COUNTDOWN) && !alarmState.equals(TRIGGERED)) {
				if (getStateName(key).equals("armed_away") || getStateName(key).equals("armed_night")) {
					logger.info("Grace period entered for state change to: " + getStateName(key));
					beep(200);
					requestedExitState = getState(key);
					requestedExitTime = new Date();
					setLedForState(requestedExitState);
					sendCommand("validate");
				} else {
					beep(200);
					sendCommand(getStateName(key));
				}
			} else if (getStateName(key).equals("disarmed")) {
				beep(200);
				sendCommand(getStateName(key));
			}
			lastKeyPressTime = null;
			clearCode();
		}
	}

	private String getStateName(char key) {
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

	private String getStateName(AlarmState alarmState) {
		switch (alarmState) {
		case ARMED_AWAY:
			return "armed_away";
		case ARMED_NIGHT:
			return "armed_night";
		case ARMED_HOME:
			return "armed_home";
		case DISARMED:
			return "disarmed";
		case COUNTDOWN:
			return "countdown";
		case TRIGGERED:
			return "triggered";
		default:
			return "unknown";
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

	private void sendCommand(String state) {
		logger.info("Sending command to update state to: " + state);

		StringBuilder requestJson = new StringBuilder();
		requestJson.append("{\"state\": \"");
		requestJson.append(state);
		requestJson.append(" ");
		requestJson.append(code);
		requestJson.append("\"}");
		logger.info("JSON to send: " + requestJson);

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
			logger.info("Code entered: " + code.toString());
			showCodeLength();
			beep(100);
		}
	}

	private void handleShowState() {
		clearCode();
		lastKeyPressTime = null;
		lastStateChangeTime = new Date();
		logger.info("Hash key pressed. Showing current state: " + getStateName(alarmState));
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
					logger.info("Grace period expired");
					requestedExitTime = null;
					sendCommand(getStateName(requestedExitState));
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