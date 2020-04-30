#### RuleMiner

The RuleMiner implements subgroup mining. In contrast to other implementation it does not use the FPGrowth algorithm but a modified version that operatorates recursively on a fixed FP tree. 

	Usage
	
	 ruleminer <configFile> : Generates subgroups that are interesting according to some quality function.
	
	Arguments
	
	   <configFile> : Configuration file

The configuration file should be a file in [HOCON format](https://github.com/lightbend/config/blob/master/HOCON.md) with the extension `.conf`. Details about the configuration options are explained in [configuration.md](file:./docs/configuration.md).

The RuleMiner is provided as a .zip file. Current version is `ruleminer-0.5.zip`. The  structure is

```hocon
  ruleminer-<version>
    - bin
		  - ruleminer      // bash shell script
		  - ruleminer.bat  // for Windows
		- docs
		  - configuration.md
	  - examples
 		  - connect4
 		    - conmfiguration.conf
 		    - data.csv
 		  - "other examples"
 		- lib // the jar files
 		- Readme.md
```
The directory `connect4` comprises a simple example for testing the setup.

- Expand `ruleminer-<version>.zip`
- `cd ruleminer-<version>` 
- `chmod u+x bin/ruleminer`
- `bin/ruleminer connect4/configuration.conf`


Contact: [axel.poigne@iais.fraunhofer.de](mailto:axel.poigne@iais.fraunhofer.de)


