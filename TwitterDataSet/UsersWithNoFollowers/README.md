Hey you. Reader. Thanks for checking this discussion out. This is the first  of hopefully many modules I'll be writing abouon my  github. 
 If you find any errors or would like to provide any feedback, feel free to drop a comment below or <a href = "mailto: dorris.s@husky.neu.edu">email</a> me. 

## MapReduce module 1: discussing map-phase filtering for an efficient *anti-join*.   


#### Problem Introduction: "Find all user IDs with 0 followers"

 In our twitter data set, as described in the main project folder, we are given *edges.csv*, where each entry is a 2-tuple:

``` (3,6) --> user_3 follows user_6 ``` 

The query requires us to find all users with no followers? If we were stuck with just the set of all edges, we
can still find users with no followers, but it would require us to duplicate the dataset.   
 
 >Note: I will tackle the design pattern of self-joins in the *Triangles* module.  
 
 Thankfully, we are also given the *nodes.csv* dataset, which consists of all distinct userIDs as line-separated integers.
  
Finding users with no followers is essentially a filtering problem. The filtering predicate lets call it p(X), is:
   P(x) = 
        * true-->  X, a member of the users set, exists in edges,  the distinct set of inlink ID2s in the edges set. 
        * false otherwise
  
If we  were working in  SQL, we  might produce the query:

```sql
SELECT ID FROM NODES 
WHERE ID NOT IN(
      SELECT DISTINCT ID2 FROM EDGES 
      )
```

Seeing the "not in" clause in our "where" statement signals that we performing an "anti-join" of the nodes and edges data. Instead of a natural join, where our result is a  subset of  A and B where our join condition  is  satisfied, we include the opposite, in that output records  dont exhibit our join predicate. With manageable-sized 
sets of that can fit on our local machines, this problem becomes straightforward. The query becomes more interesting, however, if we solve it in  parallel. 

But before diving into the MapReduce implementation to solve the "parallelization" problem, I'll look at how an anti join can be solved efficiently in a local system.    
 
 ### A Single-Machine Approach
 
Our query, again:
*  for every ID1 in U, check all possible ID2s in E to guarantee that ID1 is not a member of E.

That description points to a quadratic, brute-force algorithm. We can definitely beat the O(|U|*|E|) 
complexity by using sorting as a subroutine.
 
### What form of sorting do I use?
  QuickSort is probably the best option here, as sorts a collection without auxiliary memory.
 Because we are working with integers however, one could also think of using [counting sort](https://www.hackerearth.com/practice/algorithms/sorting/counting-sort/tutorial/).
If we sort this way, we can achieve an O(|N| + |E| time) sort, yet we use auxiliary memory in implementing the solution. 


Counting sort becomes less practical, because for a larger than memory set, we would need to:
                           
 * partition the set into monotonically increasing subsets 
    * here one would benefit from approximating good ranges, which itself can be an expensive process:
  * apply counting sort individually on those smaller ranges
     - this requires mapping from larger numbers to array indices in our larger numbered range partitions, as we exploit an arrays contant time lookups in the sorting process
   * concatenate those sorted arrays back into the original set.
   
 
If we ignore counting sort, how does our 1-process program, lets say in java, find the anti join between the users set and the edges set on edges key ID2?  
 
 After some thought, this problem easily translates to the question: 
 
 > Given 2 Sets  of integers, A and B, elements in A  are a not in B. 
 
 Set B is our ID2s extracted edges set, and Set A is just our IDs from the users here.
 
My solution below was inspiration from the classic merge procedure in mergesort...
 
```java
/*
 PseudoCode assumes users and edges wrapped in objects for simplicity
*/
class ZeroFollowers{
    public int[] zeroFollowers ( UsersSet users, EdgeSet edges){
       answer = new int[users.size()]; // buffer
       users.sort();
     
       edges.sortWithKey(edges.id2);

       int answer_index =0; //  index of answer arr we've reached, equivalent to logical size of answer. 
       int users_index = 0; // index in users of current largest ID seen
       int edges_index = 0; //index in edges of current largest ID seen 
       
       
       while (users_index<users.size() && edges_index<edges.size()){
          curr_userId = users.get(users_index);
          curr_edgesId2  = edges.getId2(edges_index); 
          
          // userId can be safely added to answer set. we would have
          // found it's matching edges ID2 if previously if existed, and moved on search  
          if (curr_userId < curr_edgesId2){
             answer[answer_index] = curr_userId;
             users_index++;
             answer_index++;
            
          }
          //  keep curr user id in contention for filtering. 
          else if (curr_userId > curr_edgesId2){
            edges_index++;
          }
          // move along both pointers as we can safely filter out user id. 
          else{
            users_index++;
            edges_index++;
          }
        }
       return answer;
       }
  }

``` 

 This works great for a list that does not need to be paged in and out of memory. Obviously, an 86 million record array, even just of of 8 bit integers, holds over a half a TB of data. Lets now see how we can approach this problem in the MapReduce framework. 

### A MapReduce Approach. 

>The following discussion assumes that the reader has familiarity with the MapReduce api. For a brief introduction, click [here](https://hci.stanford.edu/courses/cs448g/a2/files/map_reduce_tutorial.pdf). 
 
  For any possible solution, we must add both our edges and our users file as input to the mapper phase. Just as in our ZeroFollowers class above,
  we need both the users set and the edges set. 
  
  From this, I explore 2 approaches:
   
   Approach 1 is to follow the above algorithm at the Reducer task level, which requires the reduce function calls populating task-level-scope user/ edges sets, and a ending cleanup process  to  sort and  run the anti-join algorithm shown above. 
   
  With MR though, we are sending a subset of  users  and edges to different  machines, dictated by the partitioner's mapping of our chosen  key. 
     
   Lets say our partitioner was the following: 
   
          Partition(Key) =  key  %  number of reduce tasks 
           
   
  Our keys (for both data emitted from edges and nodes) are integers, so if we assume even distribution of the keyspace, we perfectly distribute load across the number of tasks that we want.
   
  But if I send IDs to a different task, only receiving subsets of users/edges in my reducer, how can I confirm that filtering works correctly? 
     
      
   To show correctness here must prove that the algorithm, ran our partitioned sets, still maintains the loop invariant given by the original algorithm:
          After an iteration i of the  while loop:
            -we currently have stored the max user id1 and edge id2 from all users and edges from  element at indices 0 to i-1.
            -if we found a match of the users and edges pair looked at during the previous iteration, we safely  ignore this pairing, and increment both index pointers.


   The correctness comes from this intuition: Even if I dont have the entire set of users and edges in a certain partition the *only* task that receives a specific key/value pair with key= X is equal to the task given by the call of Partition(X). Partition(X) always returns the same amount, so if we find no match in our partition,  it doesnt exist globally.
   
    
   This is powerful because it allows us to run our job in parallel correctly with the benefit of speedup.  
   
    The following MR psuedocode outlines our approach:
    
    
```java

class Mapper{
  
  map(Input line){
    int [] parsed = line.parse();
    
    // user node key =  ID,  value = "user"
    if (parsed.length.isUsersInput()){
      emit(parsed[0],"user");
    }
    // emit  ID2 as the line comes from our edges set.
    else{
      emit(parsed[1],"edge");
    }
    
  }
}

class Reducer{
  // setup executes before all reduce calls. 
  setup(){
    List<Integer> users = empty();
    List<Integer> edges = empty();
   
  }
  reduce(int key, iterator<String> tags){
    for (String tag : tags){
    if (tag == "user"){
      users.add(key);
    }
    
    else{
      edges.add(key);
    }
   }
  }
  // all lists have been populated,cleanup() is last
  cleanup(){
    users.sort();
    edges.sort();
    
   List<Integer> filtered_users  = anti-join-algorithm(users,edges);
    for(Integer user: filtered_users) {
      // output 
      emit(user);
    }
    
  }
  
}

```
    
Unfortunately with this approach, we still can find ourselves with a memory problem. This is because we are storing, as we call reduce, ALL edges and users sent to a  specific  task. If I only have 2 tasks, we are only halfing our data size, which still wouldn't fit comfortably in  memory. 

This is a lesson though that I learned tackling this problem, in that moving to a parallel execution environment with large data sets makes seemingly trivial problems non-trivial. 

###  MapReduce Attempt 2

We dont need the merge-inspired algorithm, as nice as it is. We also do not need our task level lists. 

We just need to:

   Mapper:
     emit (userID,"user") if we see a user  record
     emit (edge.id2,"edge") if we see an edge record.  
      
   For  our updated reducer notice that if we do  not find an edge tag in a single reduce call (reduce calls inside reducer task happen once for each given key by default), then the user has no follower anywhere in our data. Our Mapper Stays the same, and the  Reducer class becomes simpler:

```java
 class Reducer{
   reduce(int key,  iterator<String> tags){
     boolean found_match = false;
     for (String tag : tags){
        if (tag == "edge"){
          found_match = true;
          break;
     
        }
    }
    if (!found_match){
        emit(key);
      }
   }

}
 ```
       
This is cleaner, and  more memory friendly. Can we do better? In our reducer, this is as good as It gets. We look now to the combiner for a chance to optimize  in our map phase. 

The combiner acts in MapReduce as a local reducer (with the same signature and one-call per key structure) decreases data traffic across a distributed cluster if implemented correctly.  

The combiner here decreases traffic, acts as a Map-phase "pre-filter". If we find in our combiner any key with a edge ID2 record associated with it, we simply do not emit the ID to the reduce phase. If we do not find a matching ID2, we cannot know for sure it is in our split of the input dataset, and so we emit just as our mapper does. Notice  that the  Reducer Task performs the exact same computation that we need for 
the  combiner, so in our MR api, we call :
     
    job.setCombinerClass(Reducer.class) 

So there we go! Attempt 2 is what you'll find in the source code, and the BUILD.md provides the necessary steps to run the job.  
  
