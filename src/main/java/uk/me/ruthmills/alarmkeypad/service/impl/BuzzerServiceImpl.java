package uk.me.ruthmills.alarmkeypad.service;

import static com.pi4j.io.gpio.PinState.LOW;
import static com.pi4j.io.gpio.RaspiPin.GPIO_18;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.stereotype.Service;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalOutput;

@Service
public class BuzzerServiceImpl implements BuzzerService {

	private volatile GpioController gpio;
	private volatile GpioPinDigitalOutput buzzer;

	@PostConstruct
	public void initialise() {
		gpio = GpioFactory.getInstance();

		buzzer = gpio.provisionDigitalOutputPin(GPIO_18, "Buzzer", LOW);
		buzzer.setShutdownOptions(true, LOW);
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
