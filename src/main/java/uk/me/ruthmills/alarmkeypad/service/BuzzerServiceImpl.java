package uk.me.ruthmills.alarmkeypad.service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.stereotype.Service;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;

@Service
public class BuzzerServiceImpl implements BuzzerService {

	private GpioController gpio;
	private GpioPinDigitalOutput buzzer;

	@PostConstruct
	public void initialise() {
		gpio = GpioFactory.getInstance();

		buzzer = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_18, "Buzzer", PinState.LOW);
		buzzer.setShutdownOptions(true, PinState.LOW);
	}

	@PreDestroy
	public void shutdown() {
		gpio.shutdown();
	}

	public void setBuzzer(boolean buzzer) {
		if (buzzer) {
			this.buzzer.high();
		} else {
			this.buzzer.low();
		}
	}
}
