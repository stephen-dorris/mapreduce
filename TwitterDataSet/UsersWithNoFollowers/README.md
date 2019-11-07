Hey you. Reader. Thanks for checking this out. This is the first  of hopefully many modules I'll be writing  on my  github. 
 If you find any errors or would like to provide any feedback, feel free to drop a comment below or <a href = "mailto: dorris.s@husky.neu.edu">email</a> me. 

##MapReduce: map-phase filtering for an efficient *anti-join*.   



####Problem Introduction: "Find all user IDs with 0 followers"


 In our twitter data set, as described in  the main project folder, we are given *edges.csv*, where each entry is a 2-tuple:

``` (3,6) --> user_3 follows user_6 ``` 

How can we use these links to determine those with no followers? If we were stuck with just this data set, we
could still find the answer, however the method involves duplication of an arbitrarily-sized chunk of our 86 million record dataset. 

>Note: I will tackle this design pattern of self-joins in the *Triangles* module.  

Thankfully, we are also given *nodes.csv* , which consists of all possible IDs as line-seperated integers.
 This file has *only* 11 million records and we can safely say that each entry is distinct.
 
 Either way, what we have is a filtering problem. 

The filtering predicate, p(X) is simple:
    
 
   * true-->  X exists in 
 edges*, where edges* is distinct set of ID2s in edges.
   * false otherwise
  
 This leads user to the following query form, roughly translated to sql:

```sql
SELECT ID FROM NODES 
WHERE ID NOT IN(
      SELECT DISTINCT ID2 FROM EDGES 
      )
```

Seeing  the  "not in" clause  in our where statement signals that we are essentialy performing  an "anti-join". To see this, lets look at a sample 

Seems straight forward enough! Before diving into the MapReduce scripts in this file, I'll look at how this problem could be solved
in a relatively efficient manner, assuming our sets were smaller.     
 
 ### A 1-Machine Approach
 

Our query, again:
*  for every ID1 in U, check all possible ID2s in E to guarantee that ID1 is not a member of E.

Although that description points to a quadratic algorithm, in a sequential local process, with our data set more manageable,
 in size we can definitely beat the O(|U|*|E|) bound here. This sub quadratic approach would require sorting.
 
 
  QuickSort is probably the best option here, as it sorts without any need for extra memory, at a premium in this situation. 
  I did think also of [counting sort](https://www.hackerearth.com/practice/algorithms/sorting/counting-sort/tutorial/).
If we sort each using counting sort, which we can do with our discrete set, we can achieve O(|N| + |E| time) at the cost of space.

Counting Sort for an arbitrarily large data set  might require the following, even if we have a fraction of the *edges* or *users* files.
                           
 * range-partition the set into increasing, disjoint ranges of integers 
    * One would need to  approximate good ranges, which itself is an expensive profess:
  * apply counting sort individually on those smaller ranges
     - this requires mapping from larger numbers to array indices in our larger numbered range partitions
   * concatenate those sorted arrays back into the original set.
   
 
 Anyways, how  does our 1-process program, lets say in java, find these edges  with no inlink edges
 
This approach gets inspiration from the classic merge procedure in mergesort...
 
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
          curr_edgesId2 = edges.getId2(edges_index); 
          
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

 This works great as  for a list that does not need to be paged in and out of memory. Obviously, an 86 million record array, even of 8 bit integers, holds over  a half a TB of data. Lets now see how we can approached this problem in the MapReduce framework. 

### This project: A MapReduce Approach. 

>The following discussion assumes that the reader has familiarity with the MapReduce api. For a brief introduction, click [here](https://hci.stanford.edu/courses/cs448g/a2/files/map_reduce_tutorial.pdf). 


A possible parallel solution to this problem would be attempting a similar "merging" inspired procedure, shown above, in our reduce phase. 


If we use standard partitioner (function which  determines the task a data  point is sent to) based on hashing, somewhat  suprisingly, 
we can still get the correct answer  
 Lets say our hash partitioner  was:  

        HashPartition(Key) =  key  %  #tasks. 
        

   Our keys (for both data emitted from edges and nodes) is an integer, so if we assume even  distribution of keys, (a strong assumption ), this in a sense perfectly distributes load. More  importantly , this even distribution still maintains the loop invariant implied by the above merging :
        After each iteration i:
        -we currently have stored the max user  id1 and edge id2
        -if we found a match, we moved both user/ edge index pointers to ignore that user id in answer 
        
If we partition by key % taskct, its obvious that a single reducer does not have access to all possible
ids smaller than it. Therefore when I 
        


  
