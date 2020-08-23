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
import org.springframework.http.HttpMethod;
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

	private static final long STATE_CHANGE_TIMEOUT = 6000L;
	private static final long KEY_PRESS_TIMEOUT = 5000L;
	private static final long COMMAND_TIMEOUT = 4000L;
	private static final long EXIT_WARNING_TIMEOUT = 30000L;
	private static final long EXIT_TIMEOUT = 40000L;
	private static final long COUNTDOWN_WARNING_TIMEOUT = 20000L;

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
	private volatile Date lastCommandTime;
	private volatile Date lastStateChangeTime;
	private volatile Date requestedExitTime;
	private volatile AlarmState requestedExitState;
	private volatile String requestedCode;
	private volatile boolean noNormalFlashNext;

	private RestTemplate restTemplate;
	private final Logger logger = LoggerFactory.getLogger(AlarmStateServiceImpl.class);

	@PostConstruct
	public void initialise() {
		alarmState = UNKNOWN;
		code = new StringBuilder();
		restTemplate = new RestTemplate(getClientHttpRequestFactory());
		try {
			sendCommand("initialise", "");
		} catch (Exception ex) {
			logger.error("Failed to send initialise command", ex);
		}
		logger.info("Alarm State set to unknown");
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
		cancelExit();
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
		if (code.length() < 8) {
			lastKeyPressTime = new Date();
			code.append(key);
			logger.info("Code entered: " + code.toString());
			showCodeLength();
			beep(100);
		}
	}

	private void handleCommand(char key) {
		if (code.length() > 0) {
			if (!alarmState.equals(COUNTDOWN) && !alarmState.equals(TRIGGERED)) {
				if (getStateName(key).equals("armed_away") || getStateName(key).equals("armed_night")) {
					logger.info("Grace period entered for state change to: " + getStateName(key));
					beep(250);
					requestedExitState = getState(key);
					requestedExitTime = new Date();
					requestedCode = code.toString();
					setLedForState(requestedExitState);
					lastCommandTime = new Date();
					sendCommand("validate", requestedCode);
				} else {
					beep(250);
					lastCommandTime = new Date();
					sendCommand(getStateName(key), code.toString());
				}
			} else if (getStateName(key).equals("disarmed")) {
				beep(250);
				sendCommand(getStateName(key), code.toString());
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

	private void sendCommand(String state, String code) {
		logger.info("Sending command to update state to: " + state);

		StringBuilder requestJson = new StringBuilder();
		requestJson.append("{\"state\": \"");
		requestJson.append(state);
		if (code.length() > 0) {
			requestJson.append(" ");
		}
		requestJson.append(code);
		requestJson.append("\"}");
		logger.info("JSON to send: " + requestJson);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("Authorization", "Bearer " + token);

		logger.info("About to send POST to " + endpoint);
		restTemplate.postForEntity(endpoint, new HttpEntity<String>(requestJson.toString(), headers), String.class);

		logger.info("About to send DELETE to " + endpoint);
		restTemplate.exchange(endpoint, HttpMethod.DELETE, new HttpEntity<String>("", headers), String.class);
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
		beep(250);
	}

	private void showCodeLength() {
		ledService.setLeds(code.length() >= 1 ^ code.length() >= 5, code.length() >= 2 ^ code.length() >= 6,
				code.length() >= 3 ^ code.length() >= 7, code.length() >= 4 ^ code.length() >= 8);
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
				if (beforeCountdownWarningTime()) {
					flashCountdown();
				} else {
					flashCountdownWarning();
				}
			} else if (exitRequested()) {
				if (beforeExitWarningTime()) {
					flashExit();
				} else if (beforeExitTime()) {
					flashExitWarning();
				} else {
					logger.info("Grace period expired");
					requestedExitTime = null;
					sendCommand(getStateName(requestedExitState), requestedCode);
					cancelExit();
				}
			} else if (stateChanged()) {
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
		noNormalFlashNext = true;
	}

	private void flashCountdown() {
		if (!keyPressed()) {
			ledService.setLeds(false, false, false, true);
			beep(250);
		}
		flash(250, false, false, true, false);
		flash(250, false, true, false, false);
		flash(0, true, false, false, false);
		noNormalFlashNext = true;
	}

	private void flashCountdownWarning() {
		if (!keyPressed()) {
			ledService.setLeds(false, false, false, true);
			beep(250);
		}
		flash(250, false, false, true, false);
		if (!keyPressed()) {
			ledService.setLeds(false, true, false, false);
			beep(250);
		}
		flash(0, true, false, false, false);
		noNormalFlashNext = true;
	}

	private void flashState() {
		setLedForState(alarmState);
		sleep(250);
		flash(250, false, false, false, false);
		setLedForState(alarmState);
		sleep(250);
		flash(0, false, false, false, false);
		noNormalFlashNext = true;
	}

	private void flashExit() {
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
		noNormalFlashNext = true;
	}

	private void flashExitWarning() {
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
		noNormalFlashNext = true;
	}

	private void flashNormal() {
		if (!commandRequested() && LocalDateTime.now().getSecond() % 4 == 0 && !noNormalFlashNext) {
			flash(250, true, false, false, false);
		} else {
			flash(250, false, false, false, false);
			noNormalFlashNext = false;
		}
		flash(250, false, false, false, false);
		flash(250, false, false, false, false);
		flash(0, false, false, false, false);
	}

	private void cancelExit() {
		requestedExitTime = null;
		requestedExitState = null;
		requestedCode = null;
	}

	private void setLedForState(AlarmState state) {
		if (!keyPressed()) {
			ledService.setLeds(state.equals(ARMED_AWAY), state.equals(ARMED_NIGHT), state.equals(ARMED_HOME),
					state.equals(DISARMED));
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
		if (!keyPressed()) {
			ledService.setLeds(red, amber, green, blue);
			sleep(milliseconds);
		}
	}

	private boolean stateChanged() {
		return lastStateChangeTime != null
				&& new Date().getTime() - lastStateChangeTime.getTime() < STATE_CHANGE_TIMEOUT;
	}

	private boolean keyPressed() {
		return lastKeyPressTime != null && new Date().getTime() - lastKeyPressTime.getTime() < KEY_PRESS_TIMEOUT;
	}

	private boolean commandRequested() {
		return lastCommandTime != null && new Date().getTime() - lastCommandTime.getTime() < COMMAND_TIMEOUT;
	}

	private boolean exitRequested() {
		return requestedExitTime != null && requestedExitState != null;
	}

	private boolean beforeExitWarningTime() {
		return requestedExitTime != null && new Date().getTime() - requestedExitTime.getTime() < EXIT_WARNING_TIMEOUT;
	}

	private boolean beforeExitTime() {
		return requestedExitTime != null && new Date().getTime() - requestedExitTime.getTime() < EXIT_TIMEOUT;
	}

	private boolean beforeCountdownWarningTime() {
		return lastStateChangeTime != null
				&& new Date().getTime() - lastStateChangeTime.getTime() < COUNTDOWN_WARNING_TIMEOUT;
	}
}
