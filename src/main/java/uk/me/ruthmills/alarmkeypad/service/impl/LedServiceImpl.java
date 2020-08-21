package uk.me.ruthmills.alarmkeypad.service.impl;

import static com.pi4j.io.gpio.PinState.HIGH;
import static com.pi4j.io.gpio.RaspiPin.GPIO_04;
import static com.pi4j.io.gpio.RaspiPin.GPIO_17;
import static com.pi4j.io.gpio.RaspiPin.GPIO_22;
import static com.pi4j.io.gpio.RaspiPin.GPIO_27;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.stereotype.Service;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalOutput;

import uk.me.ruthmills.alarmkeypad.service.LedService;

@Service
public class LedServiceImpl implements LedService {

	private volatile GpioController gpio;
	private volatile GpioPinDigitalOutput redLed;
	private volatile GpioPinDigitalOutput amberLed;
	private volatile GpioPinDigitalOutput greenLed;
	private volatile GpioPinDigitalOutput blueLed;

	@PostConstruct
	public void initialise() {
		gpio = GpioFactory.getInstance();

		redLed = gpio.provisionDigitalOutputPin(GPIO_04, "Red LED", HIGH);
		redLed.setShutdownOptions(true, HIGH);

		amberLed = gpio.provisionDigitalOutputPin(GPIO_17, "Amber LED", HIGH);
		amberLed.setShutdownOptions(true, HIGH);

		greenLed = gpio.provisionDigitalOutputPin(GPIO_27, "Green LED", HIGH);
		greenLed.setShutdownOptions(true, HIGH);

		blueLed = gpio.provisionDigitalOutputPin(GPIO_22, "Blue LED", HIGH);
		blueLed.setShutdownOptions(true, HIGH);
	}

	@PreDestroy
	public void shutdown() {
		gpio.shutdown();
	}

	@Override
	public void setLeds(boolean red, boolean amber, boolean green, boolean blue) {
		if (red) {
			redLed.low();
		} else {
			redLed.high();
		}

		if (amber) {
			amberLed.low();
		} else {
			amberLed.high();
		}

		if (green) {
			greenLed.low();
		} else {
			greenLed.high();
		}

		if (blue) {
			blueLed.low();
		} else {
			blueLed.high();
		}
	}
}
