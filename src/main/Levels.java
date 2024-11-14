package src.main;
import javax.sound.sampled.*;

class Levels {

    public static void main(String[] args) {
			try {
					new Thread(Levels::captureAndBroadcastAudio).start();
					System.in.read();
			} catch (Exception e) {
					e.printStackTrace();
			} 
    }

    private static TargetDataLine getTargetLineByNameAndFormat(String mixerName, AudioFormat format) {
        for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
						System.out.println("Checking MixerInfo: "+mixerInfo);
            if (mixerInfo.getName().contains(mixerName) && !mixerInfo.getName().contains("Port")) {	// Exclude ports
                Mixer mixer = AudioSystem.getMixer(mixerInfo);
								System.out.println("Mixer: "+mixer);
                try {
                    DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, format);
                    if (mixer.isLineSupported(dataLineInfo)) {
                        return (TargetDataLine) mixer.getLine(dataLineInfo);
                    } else {
												System.out.println("Line not supported: " + dataLineInfo);
										}
                } catch (LineUnavailableException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    private static void captureAndBroadcastAudio() {
        try {
            String name = "E.A.R.S Gain: 18dB";
            AudioFormat format = new AudioFormat(48000, 24, 2, true, true);
						System.out.println("Name: '" + name +"' AudioFormat: "+format);

            TargetDataLine targetDataLine = getTargetLineByNameAndFormat(name, format);
            if (targetDataLine == null) {
                System.out.println("Name '" + name + "' not found.");
                return;
            }

            targetDataLine.open(format);
            targetDataLine.start();

            byte[] buffer = new byte[4092]; // Read buffer size should be a multiple of 6 for 24-bit stereo
						long lastPrintTime = System.currentTimeMillis();
						long counter = 0;

            while (true) {
                int bytesRead = targetDataLine.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
									int monoSum = 0;
									int leftSum = 0;
									int rightSum = 0;

									// asume mono
									for (int i = 0; i < bytesRead; i += 3) {
										int monoChannel = ((buffer[i] & 0xFF) << 16) | ((buffer[i + 1] & 0xFF) << 8) | (buffer[i + 2] & 0xFF);
										if ((monoChannel & 0x800000) != 0) { // Handle sign bit for 24-bit
											monoChannel |= 0xFF000000;
										}
										monoSum += monoChannel * monoChannel;
									}

									// assume stereo
									for (int i = 0; i < bytesRead; i += 6) {
										// Read left channel (first 3 bytes)
										int leftChannel = ((buffer[i] & 0xFF) << 16) | ((buffer[i + 1] & 0xFF) << 8) | (buffer[i + 2] & 0xFF);
										if ((leftChannel & 0x800000) != 0) { // Handle sign bit for 24-bit
												leftChannel |= 0xFF000000;
										}
										leftSum += leftChannel * leftChannel;

										// Read right channel (next 3 bytes)
										int rightChannel = ((buffer[i + 3] & 0xFF) << 16) | ((buffer[i + 4] & 0xFF) << 8) | (buffer[i + 5] & 0xFF);
										if ((rightChannel & 0x800000) != 0) { // Handle sign bit for 24-bit
												rightChannel |= 0xFF000000;
										}
										rightSum += rightChannel * rightChannel;
									}

									double max = Math.pow(2, 23) - 1;
									double referenceSPL = 94.0;
									double stereoCount = bytesRead / 6;
									double monoCount = bytesRead / 3;

									double monoRMS = Math.sqrt(monoSum / monoCount);
									double leftRMS = Math.sqrt(leftSum / stereoCount);
									double rightRMS = Math.sqrt(rightSum / stereoCount);

									double monoDBFS = 20 * Math.log10(monoRMS / max);
									double leftDBFS = 20 * Math.log10(leftRMS / max);
									double rightDBFS = 20 * Math.log10(rightRMS / max);

									double monoDBSPL = monoDBFS + referenceSPL;
									double leftDBSPL = leftDBFS + referenceSPL;
									double rightDBSPL = rightDBFS + referenceSPL;

									long currentTime = System.currentTimeMillis();
									if (currentTime - lastPrintTime >= 1000) {  // Check if 1000 ms (1 second) has passed
										System.out.printf(""
											+ " Read: "
											+ String.format("%d", bytesRead)
											+ " Count "
											+ String.format("%4d", counter)

											+ " Mono: " 
											+ String.format("%9.2f", monoRMS)
											+ " RMS, " 
											+ String.format("%7.2f", monoDBFS)
											+ " dBFS, " 
											+ String.format("%7.2f", monoDBSPL)
											+ " dBSPL, " 

											+ " Left: " 
											+ String.format("%9.2f", leftRMS)
											+ " RMS, " 
											+ String.format("%7.2f", leftDBFS)
											+ " dBFS, " 
											+ String.format("%7.2f", leftDBSPL)

											+ " dBSPL Right:" 
											+ String.format("%9.2f", rightRMS)
											+ " RMS, " 
											+ String.format("%7.2f", rightDBFS) 
											+ " dBFS " 
											+ String.format("%7.2f", rightDBSPL)
											+ " dBSPL"
											+"\n"
										);
										lastPrintTime = currentTime;
										counter++;
									}
								}
            }
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }

}