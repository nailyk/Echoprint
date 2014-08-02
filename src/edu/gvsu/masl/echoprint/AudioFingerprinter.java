/**
 * AudioFingerprinter.java
 * EchoprintLib
 * 
 * Created by Alex Restrepo on 1/22/12.
 * Copyright (C) 2012 Grand Valley State University (http://masl.cis.gvsu.edu/)
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package edu.gvsu.masl.echoprint;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

/**
 * Main fingerprinting class<br>
 * This class will record audio from the microphone and generate the fingerprint
 * code using a native library
 * 
 * @author Alex Restrepo (MASL)
 * 
 */
public class AudioFingerprinter implements Runnable {
	private final int SAMPLE_RATE = 11025;
	private final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
	private final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;

	private Thread thread;
	AudioRecord mRecordInstance = null;

	private short audioData[];
	private int bufferSize;
	private int secondsToRecord;

	private AudioFingerprinterListener listener;

	/**
	 * Constructor for the class
	 * 
	 * @param listener is the AudioFingerprinterListener that will receive the callbacks
	 */
	public AudioFingerprinter(AudioFingerprinterListener listener) {
		this.listener = listener;
	}

	/**
	 * Starts the listening / fingerprinting process using the default parameters:
	 * A single listening pass of 20 seconds
	 */
	public void fingerprint() {
		// set default listening time to 20 seconds
		this.fingerprint(20);
	}

	/**
	 * Starts the listening / fingerprinting process
	 * 
	 * @param seconds
	 *            the number of seconds to record per pass
	 */
	public void fingerprint(int seconds) {
		// capture 30 seconds max, 10 seconds min.
		this.secondsToRecord = Math.max(Math.min(seconds, 30), 10);

		// start the recording thread
		thread = new Thread(this);
		thread.start();
	}

	/**
	 * stops the listening / fingerprinting process if there's one in process
	 */
	public void stop() {
		if (mRecordInstance != null)
			mRecordInstance.stop();
	}

	/**
	 * The main thread
	 * Records audio and generates the audio fingerprint, then it queries the
	 * server for a match and forwards the results to the listener.
	 */
	public void run() {
		try {
			// create the audio buffer
			// get the minimum buffer size
			int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING);

			// and the actual buffer size for the audio to record
			// SAMPLE_RATE * seconds to record.
			bufferSize = Math.max(minBufferSize, this.SAMPLE_RATE * this.secondsToRecord);

			audioData = new short[bufferSize];

			// start recorder
			mRecordInstance = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, ENCODING, minBufferSize);

			willStartListening();
			mRecordInstance.startRecording();
			// fill audio buffer with mic data.
			int samplesIn = 0;
			do {
				samplesIn += mRecordInstance.read(audioData, samplesIn, bufferSize - samplesIn);
				if (mRecordInstance.getRecordingState() == AudioRecord.RECORDSTATE_STOPPED)
					break;
			} while (samplesIn < bufferSize);

			if (mRecordInstance.getRecordingState() == AudioRecord.RECORDSTATE_STOPPED)
			{
				mRecordInstance.release();
				mRecordInstance = null;
				didInterrupted();
				return;
			}
			
			// create an echoprint codegen wrapper and get the code
			Codegen codegen = new Codegen();
			String fp_code = codegen.generate(audioData, samplesIn);

			if (fp_code.length() == 0)
				throw new Exception("unable to generate the audio fingerprint");

			if (mRecordInstance != null) {
				mRecordInstance.stop();
				mRecordInstance.release();
				mRecordInstance = null;
			}
			didFinishListening(fp_code);
		}
		catch (Exception e) {
			e.printStackTrace();
			didFailWithException(e);
		}
	}
	
	private void didFinishListening(final String fp_code) {
		if (listener == null)
			return;

		if (listener instanceof Activity) {
			Activity activity = (Activity) listener;
			activity.runOnUiThread(new Runnable() {
				public void run() {
					listener.didFinishListening(fp_code);
				}
			});
		} else
			listener.didFinishListening(fp_code);
	}

	private void willStartListening() {
		if (listener == null)
			return;

		if (listener instanceof Activity) {
			Activity activity = (Activity) listener;
			activity.runOnUiThread(new Runnable() {
				public void run() {
					listener.willStartListening();
				}
			});
		} else
			listener.willStartListening();
	}

	private void didFailWithException(final Exception e) {
		if (listener == null)
			return;

		if (listener instanceof Activity) {
			Activity activity = (Activity) listener;
			activity.runOnUiThread(new Runnable() {
				public void run() {
					listener.didFailWithException(e);
				}
			});
		} else
			listener.didFailWithException(e);
	}
	
	private void didInterrupted() {
		if (listener == null)
			return;

		if (listener instanceof Activity) {
			Activity activity = (Activity) listener;
			activity.runOnUiThread(new Runnable() {
				public void run() {
					listener.didInterrupted();
				}
			});
		} else
			listener.didInterrupted();
	}

	/**
	 * Interface for the fingerprinter listener<br>
	 * Contains the different delegate methods for the fingerprinting process
	 * 
	 * @author Alex Restrepo
	 * 
	 */
	public interface AudioFingerprinterListener {
		/**
		 * Called when the fingerprinter process loop has finished
		 */
		public void didFinishListening(String fp_code);

		/**
		 * Called when the fingerprinter is about to start
		 */
		public void willStartListening();

		/**
		 * Called if there is an error / exception in the fingerprinting process
		 * 
		 * @param e an exception with the error
		 */
		public void didFailWithException(Exception e);

		/**
		 * Called if the fingerprinter process has been interrupted
		 */
		public void didInterrupted();
	}
}