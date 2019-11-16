package mr;

import java.io.File;
import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.Appender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;


/**
 * class: NoFollowers
 *
 * Job Outputs all Users in Twitter  DataSet with no Followers, represented by Nodes with no
 * incoming edges.
 *
 * Flexibility is offered to filter down dataset size to see quicker performance using a simple
 * Max-ID Predicate .
 *
 * For build details see deploy.md
 *
 * @author Stephen Dorris
 */
public class NoFollowers extends Configured implements Tool {

  private static Logger logger = LogManager.getRootLogger();

  //  returns id(s) iff entries is 1 or 2  and contain only parseable integers. null otherwise.
  private static int[] parseToInt(String[] entries) {
    if (entries.length >= 1 && entries.length <= 2) {
      try {
        int id1 = Integer.parseInt(entries[0]);
        if (entries.length == 2) {
          int id2 = Integer.parseInt(entries[1]);
          return new int[]{id1, id2};
        }
        return new int[]{id1};

      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;

  }


  /**
   * Mapper outputs
   */
  public static class FollowersMapper extends Mapper<Object, Text, IntWritable, Text> {
    // denotes  ID output key sourced from users set
    final Text userTag = new Text("user");

    // denotes  ID output key sourced from edges set, when paired with user of same ID we ignore that ID.
    final Text edgeID2Tag = new Text("edgeID2");

    @Override
    public void map(final Object key, final Text entry, final Context context) throws IOException, InterruptedException {
      int[] parsed_entry = parseToInt(entry.toString().split(","));
      if (parsed_entry  == null) {
        System.err.println("Map Task received  incorrect entry. Check files. Aborting job.");
        System.exit(1);
      }

      Integer filter = Integer.MAX_VALUE;
      if (context.getConfiguration().get("filter-check").equals("true")){
        filter  = Integer.parseInt("filter-amt");
      }

      // Edges input file is has 2 IDs per line
      if (parsed_entry.length == 2 && parsed_entry[0] < filter && parsed_entry[1] < filter) {
        //IntWritable ID2 = new IntWritable(Integer.parseInt(parsed_entry[1]));
        context.write(new IntWritable(parsed_entry[1]), edgeID2Tag);
      }
      // Users input file  has 1 ID per
      else if (parsed_entry[0] < filter){
      //  IntWritable userID = new IntWritable(Integer.parseInt(parsed_entry[0]));
         context.write(new IntWritable(parsed_entry[0]), userTag);
      }
    }
  }

  /**
   * Only Writes edges where there is no inlink (edgeID2).
   */
  public static class FilteringReducer extends Reducer<IntWritable, Text, IntWritable, NullWritable> {
    private final NullWritable nullValue = NullWritable.get();
    final String edgeID2Tag = "edgeID2";

    @Override
    public void reduce(final IntWritable key, final Iterable<Text> values, final Context context) throws IOException, InterruptedException {
      boolean foundMatch = false;
      for (final Text tag : values) {
        if (tag.toString().equals(edgeID2Tag)) {
          foundMatch = true;
          break;
        }
      }
      if (!foundMatch) {
        context.write(key, nullValue);
      }
    }
  }

  @Override
  public int run(final String[] args) throws Exception {
    Configuration conf = getConf();
    // Parse custom build args
    final String usage = "Usage: <data-in-path> <res-out-path> [-max filter_amount] [-log <log-path>]";
    try {
      boolean activatedMaxFilter = false;
      for (int i = 2; i<args.length; i++){
        if (args[i].equals("-log")){
          FileAppender fa = new FileAppender(new PatternLayout(),args[++i]);
          fa.activateOptions();
          logger.addAppender(fa);
        }
        else if (args[i].equals("-max")){
          activatedMaxFilter = true;
          conf.set("filter-check","true");
          conf.set("filter-amt",args[++i]);

        }

      }
      if(!activatedMaxFilter){
        conf.set("filter-check","false");
      }
    }catch (Exception e){
      System.out.println("Invalid args, see  usage/deploy.md");
      System.out.println(usage);
      return 1;
    }



    // Job Configured Properly.
    final Job job = Job.getInstance(conf, "NoFollowers");
    job.setJarByClass(NoFollowers.class);
    job.setMapperClass(FollowersMapper.class);

    // Pre Shuffle Phase Pruning.
    job.setCombinerClass(FilteringReducer.class);

    job.setReducerClass(FilteringReducer.class);
    job.setOutputKeyClass(IntWritable.class);
    job.setOutputValueClass(NullWritable.class);
    FileInputFormat.addInputPath(job, new Path(args[0]));
    FileInputFormat.addInputPath(job, new Path(args[1]));
    FileOutputFormat.setOutputPath(job, new Path(args[2]));
    return job.waitForCompletion(true) ? 0 : 1;

  }


  public static void main(final String[] args) {

    try {
      FileSystem local = FileSystem.get(new Configuration());
      System.out.println(local.getWorkingDirectory().toUri());
      // ToolRunner.run(new NoFollowers(), args);
    } catch (final Exception e) {
      logger.error("", e);
    }
  }

}