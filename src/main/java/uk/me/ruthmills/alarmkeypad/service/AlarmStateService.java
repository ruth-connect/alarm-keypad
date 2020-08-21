package uk.me.ruthmills.alarmkeypad.service;

public interface AlarmStateService {

	public void armedAway();

	public void armedNight();

	public void armedHome();

	public void disarmed();

	public void countdown();

	public void triggered();

	public void invalidCode();

	public void keyPressed(char key);

	public void tick();

	public void sleep(int milliseconds);
}
