package cardScore;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.filecache.DistributedCache;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;

import com.up.util.Constant;
import com.up.util.MD5;
import com.up.util.MccTransfer;
import com.up.util.TagUtility;

/**
 * 根据持卡人的月均金额和全局百分位点，计算每个持卡人的消费力得分
 * */
public class ScoreDriven extends Configured{
	
	/**
	 * @options
	 * @param otherArgs[0]	cupsdataPath
	 * @param otherArgs[1]	output
	 * @param otherArgs[2]	cardBinPath
	 * @param otherArgs[3]	inPlatinumCardPath
	 * @param otherArgs[4]	startDate
	 * @param otherArgs[5]	endDate
	 * */
	public static void main(String[] args) throws Exception{

		//conf.set("mapred.min.split.size", "4294967296");
		//conf.set("mapred.job.queue.name", "queue3");
		Configuration conf =new Configuration();
		conf.set("mapred.min.split.size", "1073741824");
//		conf.set("mapreduce.job.queuename", "default");
//		conf.set("mapred.job.queue.name", "default");	
		String mapreduceQueueName = "root.default";
		conf.set("mapreduce.job.queuename", mapreduceQueueName);
		
		conf.addResource("classpath:core-site.xml" );
		conf.addResource("classpath:hdfs-site.xml" );
		conf.addResource("classpath:mapred-site.xml" );
		conf.addResource("classpath:yarn-site.xml" );
		conf.set("fs.hdfs.impl", org.apache.hadoop.hdfs.DistributedFileSystem.class.getName());
		conf.set("fs.file.impl",org.apache.hadoop.fs.LocalFileSystem.class.getName());
		
//		String yarnApplicationClasspath = "/etc/hadoop/conf:/opt/cloudera/parcels/CDH-5.2.1-1.cdh5.2.1.p0.36/lib/hadoop/libexec/../../hadoop/lib/*:/opt/cloudera/parcels/CDH-5.2.1-1.cdh5.2.1.p0.36/lib/hadoop/libexec/../../hadoop/.//*:/opt/cloudera/parcels/CDH-5.2.1-1.cdh5.2.1.p0.36/lib/hadoop/libexec/../../hadoop-hdfs/./:/opt/cloudera/parcels/CDH-5.2.1-1.cdh5.2.1.p0.36/lib/hadoop/libexec/../../hadoop-hdfs/lib/*:/opt/cloudera/parcels/CDH-5.2.1-1.cdh5.2.1.p0.36/lib/hadoop/libexec/../../hadoop-hdfs/.//*:/opt/cloudera/parcels/CDH-5.2.1-1.cdh5.2.1.p0.36/lib/hadoop/libexec/../../hadoop-yarn/lib/*:/opt/cloudera/parcels/CDH-5.2.1-1.cdh5.2.1.p0.36/lib/hadoop/libexec/../../hadoop-yarn/.//*:/opt/cloudera/parcels/CDH-5.2.1-1.cdh5.2.1.p0.36/lib/hadoop/libexec/../../hadoop-mapreduce/lib/*:/opt/cloudera/parcels/CDH-5.2.1-1.cdh5.2.1.p0.36/lib/hadoop/libexec/../../hadoop-mapreduce/.//*"; 
//		String mapreduceFrameworkName = "yarn";
//		
//		String mapreduceJobhistoryAddress = "bB0103004:10020";                             //********
//		String mapreduceJobhistoryDoneDir = "/user/history/done";
//		String mapreduceJobhistoryIntermediateDoneDir = "/user/history/done_intermediate";
//		
//		conf.set("fs.defaultFS", "hdfs://bB0103002:8020");
//		conf.set("yarn.application.classpath", yarnApplicationClasspath);
//		conf.set("mapreduce.framework.name", mapreduceFrameworkName);
//		
//		conf.set("mapreduce.jobhistory.address", mapreduceJobhistoryAddress);
//		conf.set("mapreduce.jobhistory.done-dir", mapreduceJobhistoryDoneDir);
//		conf.set("mapreduce.jobhistory.intermediate-done-dir", mapreduceJobhistoryIntermediateDoneDir);
//		conf.set("mapreduce.app-submission.cross-platform", "true");
//		conf.set("fs.hdfs.impl", org.apache.hadoop.hdfs.DistributedFileSystem.class.getName());
//		conf.set("fs.file.impl",org.apache.hadoop.fs.LocalFileSystem.class.getName());
//		
//		//ResourceManager存在HA机制
//		conf.set("yarn.resourcemanager.ha.enabled", "true");
//		conf.set("yarn.resourcemanager.recovery.enabled", "true");
//		conf.set("yarn.resourcemanager.store.class", "org.apache.hadoop.yarn.server.resourcemanager.recovery.ZKRMStateStore");
//		conf.set("yarn.resourcemanager.ha.rm-ids", "rm690,rm657");
//		conf.set("yarn.resourcemanager.zk-address", "bB0103003:2181,bB0103004:2181,bB0103002:2181");
//		conf.set("yarn.resourcemanager.address.rm690"," bB0103002:8032");
//		conf.set("yarn.resourcemanager.address.rm657"," bB0103003:8032");
//		conf.set("yarn.resourcemanager.scheduler.address.rm690"," bB0103002:8030");
//		conf.set("yarn.resourcemanager.scheduler.address.rm657"," bB0103003:8030");
//		
//		//NameNode也存在HA机制
//		conf.set("fs.defaultFS", "hdfs://nameservice1");
//		conf. set("dfs.nameservices", "nameservice1");
//		conf.set("dfs.ha.namenodes.nameservice1", "namenode445,namenode684");
//		conf.set("dfs.client.failover.proxy.provider.nameservice1", "org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider");
//		conf.set("dfs.namenode.rpc-address.nameservice1.namenode445", "bB0103002:8020");
//		conf.set("dfs.namenode.rpc-address.nameservice1.namenode684", "bB0103003:8020");



		
		String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
		
		//DistributedCache.addCacheFile(new Path(cardbinPath).toUri(), conf); 
		FileSystem fs = FileSystem.get(conf);
		fs.delete(new Path(otherArgs[1]),true);
		Job job = new Job(conf, "ConsumptionTag Score");
		job.setJarByClass(ScoreDriven.class);
		
		job.setMapperClass(ScoreMapper.class);
		job.setReducerClass(ScoreReducer.class);

		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);
		job.setNumReduceTasks(0);                    //这边其实就是只要map的结果，不要reduce了，最简单的方法就是在ScoreDriven里面设置job.setNumReduceTasks(0);这样就把map的结果直接输出到指定的输出目录中去了
		
		
		//FileInputFormat.addInputPath(job, new Path(inPlatinumCardPath));
		FileInputFormat.addInputPath(job, new Path(otherArgs[0]));
		FileOutputFormat.setOutputPath(job, new Path(otherArgs[1]));
		//return job.waitForCompletion(true);
		System.exit(job.waitForCompletion(true) ? 0 : 1);

	}
}
