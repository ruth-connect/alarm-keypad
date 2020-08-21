package uk.me.ruthmills.alarmkeypad.service;

import static com.pi4j.io.gpio.PinPullResistance.PULL_UP;
import static com.pi4j.io.gpio.PinState.HIGH;
import static com.pi4j.io.gpio.RaspiPin.GPIO_06;
import static com.pi4j.io.gpio.RaspiPin.GPIO_12;
import static com.pi4j.io.gpio.RaspiPin.GPIO_13;
import static com.pi4j.io.gpio.RaspiPin.GPIO_16;
import static com.pi4j.io.gpio.RaspiPin.GPIO_19;
import static com.pi4j.io.gpio.RaspiPin.GPIO_20;
import static com.pi4j.io.gpio.RaspiPin.GPIO_21;
import static com.pi4j.io.gpio.RaspiPin.GPIO_26;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.Pin;

@Service
public class AlarmKeypadServiceImpl implements AlarmKeypadService {

	private static final Pin COLUMN_PINS[] = { GPIO_06, GPIO_13, GPIO_19, GPIO_26 };
	private static final Pin ROW_PINS[] = { GPIO_12, GPIO_16, GPIO_20, GPIO_21 };
	private static final char MATRIX[][] = { { '1', '2', '3', 'A' }, { '4', '5', '6', 'B' }, { '7', '8', '9', 'C' },
			{ '*', '0', '#', 'D' } };

	@Autowired
	private AlarmStateService alarmStateService;

	private volatile GpioController gpio;
	private volatile GpioPinDigitalOutput[] columns;
	private volatile GpioPinDigitalInput[] rows;
	private volatile boolean shutdown;

	@PostConstruct
	public void initialise() {
		gpio = GpioFactory.getInstance();

		columns = new GpioPinDigitalOutput[4];
		for (int col = 0; col < COLUMN_PINS.length; col++) {
			columns[col] = gpio.provisionDigitalOutputPin(COLUMN_PINS[col], "Column " + (col + 1), HIGH);
			columns[col].setShutdownOptions(true, HIGH);
		}

		rows = new GpioPinDigitalInput[4];
		for (int row = 0; row < ROW_PINS.length; row++) {
			rows[row] = gpio.provisionDigitalInputPin(ROW_PINS[row], "Row " + (row + 1), PULL_UP);
			rows[row].setShutdownOptions(true, HIGH);
		}

		Runnable runnable = new AlarmKeypadRunnable();
		Thread thread = new Thread(runnable);
		thread.start();
	}

	@PreDestroy
	public void shutdown() {
		shutdown = true;
	}

	private class AlarmKeypadRunnable implements Runnable {

		@Override
		public void run() {
			while (!shutdown) {
				for (int col = 0; col < columns.length; col++) {
					columns[col].low();
					for (int row = 0; row < rows.length; row++) {
						if (rows[row].isLow()) {
							alarmStateService.keyPressed(MATRIX[row][col]);
							while (rows[row].isLow()) {
								alarmStateService.sleep(10);
							}
						}
					}
					columns[col].high();
				}
				alarmStateService.sleep(10);
			}

			gpio.shutdown();
		}
	}
}
