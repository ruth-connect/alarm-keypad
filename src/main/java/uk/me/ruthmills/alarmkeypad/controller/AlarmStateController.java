package uk.me.ruthmills.alarmkeypad.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AlarmStateController {

	@PostMapping(value = "/disarmed")
	public void disarmed() {

	}

	@PostMapping(value = "/armed_away")
	public void armedAway() {

	}

	@PostMapping(value = "/armed_home")
	public void armedHome() {

	}

	@PostMapping(value = "/armed_night")
	public void armedNight() {

	}

	@PostMapping(value = "/countdown")
	public void countdown() {

	}

	@PostMapping(value = "/triggered")
	public void triggered() {

	}
}
