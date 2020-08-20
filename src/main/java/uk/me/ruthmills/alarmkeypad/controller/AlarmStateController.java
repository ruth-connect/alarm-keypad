package uk.me.ruthmills.alarmkeypad.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AlarmStateController {

	@PostMapping(value = "/disarmed")
	@ResponseStatus(value = HttpStatus.OK)
	public void disarmed() {

	}

	@PostMapping(value = "/armed_away")
	@ResponseStatus(value = HttpStatus.OK)
	public void armedAway() {

	}

	@PostMapping(value = "/armed_home")
	@ResponseStatus(value = HttpStatus.OK)
	public void armedHome() {

	}

	@PostMapping(value = "/armed_night")
	@ResponseStatus(value = HttpStatus.OK)
	public void armedNight() {

	}

	@PostMapping(value = "/countdown")
	@ResponseStatus(value = HttpStatus.OK)
	public void countdown() {

	}

	@PostMapping(value = "/triggered")
	@ResponseStatus(value = HttpStatus.OK)
	public void triggered() {

	}
}
