#### RuleMiner

The RuleMiner implements subgroup mining. in contrast o ther implementation it does not use the FPGrowth algorithm but a modified version that operatorates recursively on a fixed FP tree. 

	Usage

	 ruleminer <configFile> : Generates subgroups that are interesting according to some quality function.
	
	Arguments
	
	   <configFile> : Configuration file

The configuration file should be a file in HOCON format with the extension `.conf`. Details about the configuration options are explained in [configuration.pdf](file:./configuration.pdf).

The RuleMiner is provided as a .zip file. Current version is `ruleminer-0.3.zip`. The  structure is

	- bin
		- ruleminer      // bash shell script
		- ruleminer.bat  // for Windows
	- example
	 	- connect4.conf
	 	- connect4.csv  
 	- lib               // the .jar files

The directory `example` comprises a simple example for testing the setup.

- Expand `ruleminer-0.3.zip`
- In the directory `ruleminer-0.3` 
	- run `chmod u+x bin/ruleminer`
	- run `bin/ruleminer example/connect4.conf`


Contact: [axel.poigne@iais.fraunhofer.de](mailto:axel.poigne@iais.fraunhofer.de)


