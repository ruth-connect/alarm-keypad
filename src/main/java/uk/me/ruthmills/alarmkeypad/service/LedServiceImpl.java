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
public class LedServiceImpl implements LedService {

	private GpioController gpio;
	private GpioPinDigitalOutput redLed;
	private GpioPinDigitalOutput amberLed;
	private GpioPinDigitalOutput greenLed;
	private GpioPinDigitalOutput blueLed;

	@PostConstruct
	public void initialise() {
		gpio = GpioFactory.getInstance();

		redLed = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_04, "Red LED", PinState.HIGH);
		redLed.setShutdownOptions(true, PinState.HIGH);

		amberLed = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_17, "Amber LED", PinState.HIGH);
		amberLed.setShutdownOptions(true, PinState.HIGH);

		greenLed = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_27, "Green LED", PinState.HIGH);
		greenLed.setShutdownOptions(true, PinState.HIGH);

		blueLed = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_22, "Blue LED", PinState.HIGH);
		blueLed.setShutdownOptions(true, PinState.HIGH);
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
