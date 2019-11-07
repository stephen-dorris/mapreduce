# MapReduce

This repo houses a selected subset MapReduce Jobs ive implemented as I continue to learn about  data processing in a distributed environment.
 
 One reason I find MapReduce worth learning is that no matter how simple  a solution might seem on a single machine, algorithms dont sometimes easily translate to a parallel setting, especially given  the MR's restrictions including 
  * no between  task memory sharing
  * one-shuffle, one job paradigm
  * restricted key/value pipeline between Map-phase and Reduce-phase.
 
 
 In each job submodule, ill populate 2 notable files:
 
   ####readme.md
  * my thought process as I dissecting the challenge (runtime,space complexity, i/o).
  * my solution, mainly in pseudocode. 
  
   
   
   ####deployment.md
  * notes updates on usability / deployment
   * empirical performance measurments. 



Thanks  for  reading!

