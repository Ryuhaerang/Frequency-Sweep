# Frequency Sweep
This repository contains the kotlin code for research project "Detective Echo" conducted during 2019 Fall, in CS442.

# Configurations
--- Default variables for the sound synthesis ---
var sawMode = false
var t: Thread? = null
var isRunning = false
val sr = 44100              // maximum frequency
val twopi = 2 * Math.PI
var amp = 10000             // amplitude
var fr = 10000.0            // frequency
var ph = 0.0                // phase

# App feature
There are several GUI. 
First, by moving the button on the bar or clicking plus/minus button, you can change the start frequency of the signal.
Then, by clicking play button, the designed signal is emitted and recorded.
The recrding file includes direct path, echoed signal, and background as written in final report.
