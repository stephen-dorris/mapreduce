This folder holds a few MR jobs done on a simple yet very large twitter data set. 


The data is stored in two csv files.

   - nodes.csv 11,316,811 entries.
            
       - just a long sequence of unique user IDs 
       - not very much can be done with this set alone.

   - edges.csv 85,331,846 entries.
        - we are given (ID1,ID2) pairs representing directed edges in a simple, cyclic graph. 

What is cool about this data set  is that, even though it has simple ints with no features other than following a A->B relationship, we can  explore interesting queries: 
		
	 - User with no followers.
	 - Finding a "Triangle"  A->B->C->A Cylce.  
	 - Generate importance metric == PageRank problem.           
		



Data Set Source: http://socialcomputing.asu.edu/datasets/Twitter 


R. Zafarani and H. Liu, (2009). Social Computing Data Repository at ASU [http://socialcomputing.asu.edu]. Tempe, AZ: Arizona State University, School of Computing, Informatics and Decision Systems Engineering.
