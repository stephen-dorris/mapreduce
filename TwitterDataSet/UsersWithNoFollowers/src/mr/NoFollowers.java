package mr;

import java.io.IOException;
import java.util.StringTokenizer;

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
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public class NoFollowers extends Configured implements Tool {
  private static final Logger logger = LogManager.getLogger(NoFollowers.class);


  public static class FollowersMapper extends Mapper<Object, Text, IntWritable, Text> {
    final Text userTag = new Text("user");
    final Text edgeID2Tag = new Text("edgeID2");

    @Override
    public void map(final Object key, final Text entry, final Context context) throws IOException, InterruptedException {

      String[] parsed_entry = entry.toString().split(",");
      if (parsed_entry.length != 2 && parsed_entry.length != 1) {
        System.err.println("Map Task must either receive 'ID' or 'ID1,ID2' entries");
        System.exit(1);
      }

      if (parsed_entry.length == 2) {
        IntWritable ID2 = new IntWritable(Integer.parseInt(parsed_entry[1]));
        context.write(ID2, edgeID2Tag);
      } else {
        IntWritable userID = new IntWritable(Integer.parseInt(parsed_entry[0]));
      }

    }
  }

  public static class FilteringReducer extends Reducer<IntWritable, Text, IntWritable, NullWritable> {
    private final NullWritable nullValue = NullWritable.get();
    final Text edgeID2Tag = new Text("edgeID2");
    @Override
    public void reduce(final IntWritable key, final Iterable<Text> values, final Context context) throws IOException, InterruptedException {
      boolean foundMatch = false;
      for (final Text tag : values) {
        if (tag.toString().equals(edgeID2Tag.toString())){
          foundMatch = true;
          break;
        }
      }
      if (!foundMatch){
        context.write(key,nullValue);
      }
    }
  }

  @Override
  public int run(final String[] args) throws Exception {
    final Configuration conf = getConf();
    final Job job = Job.getInstance(conf, "NoFollwers");
    job.setJarByClass(NoFollowers.class);
    final Configuration jobConf = job.getConfiguration();

//
//		final FileSystem fileSystem = FileSystem.get(conf);
//  	if (fileSystem.exists(new Path(args[1]))) {
//  			fileSystem.delete(new Path(args[1]), true);
//  		}


    job.setMapperClass(FollowersMapper.class);
    job.setCombinerClass(FilteringReducer.class);
    job.setReducerClass(FilteringReducer.class);
    job.setOutputKeyClass(IntWritable.class);
    job.setOutputValueClass(NullWritable.class);
    FileInputFormat.addInputPath(job, new Path(args[0]));
    FileInputFormat.addInputPath(job, new Path(args[1]));
    FileOutputFormat.setOutputPath(job,new Path(args[2]));
    return job.waitForCompletion(true) ? 0 : 1;

  }

  public static void main(final String[] args) {
    if (args.length != 3) {
      throw new Error("Three arguments required:\n<users-input-dir> <edges-input-dir> <output-dir>");
    }

    try {
      ToolRunner.run(new NoFollowers(), args);
    } catch (final Exception e) {
      logger.error("", e);
    }
  }

}