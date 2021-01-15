package com.wmy.ct.analysis.io;

import com.wmy.ct.common.util.JDBCUtil;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.output.FileOutputCommitter;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * MySQL数据格式化输入对象
 *
 * @author WUMINGYANG
 * @version 1.0
 * @date 2021/1/13
 */
public class MySQLTextOutputFormat_redis extends OutputFormat<Text, Text> {

    protected static class MySQLRecordWriter extends RecordWriter<Text, Text> {

        private Connection connection = null;
        private Jedis jedis = null;

        public MySQLRecordWriter() {
            // 获取资源
            connection = JDBCUtil.getConnection();
            jedis = new Jedis("bigdata111", 6379);
        }

        /**
         * 输出数据
         * @param key
         * @param value
         * @throws IOException
         * @throws InterruptedException
         */
        public void write(Text key, Text value) throws IOException, InterruptedException {

            String[] values = value.toString().split("_");
            String sumCall = values[0];
            String sumDuration = values[1];

            PreparedStatement pstat = null;
            try {
                //String insertSQL = "insert into ct_call_copy( name, date, sumCall, sumDuration ) values ( ?, ?, ?, ? )";
                String insertSQL = "insert into ct_call( name, date, sumCall, sumDuration ) values ( ?, ?, ?, ? )";
                pstat = connection.prepareStatement(insertSQL);

                String k = key.toString();
                String[] ks = k.split("_");

                String tel = ks[0];
                String date = ks[1];

                Timestamp.valueOf(date);

                // pstat.setString(1, tel);
                // pstat.setString(2, date);
                pstat.setString(1, jedis.hget("ct_user", tel));
                pstat.setString(2, date);
                pstat.setInt(3, Integer.parseInt(sumCall) );
                pstat.setInt(4, Integer.parseInt(sumDuration));
                pstat.executeUpdate();

            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                if ( pstat != null ) {
                    try {
                        pstat.close();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        /**
         * 释放资源
         * @param context
         * @throws IOException
         * @throws InterruptedException
         */
        public void close(TaskAttemptContext context) throws IOException, InterruptedException {
            if ( connection != null ) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }

            if ( jedis != null ) {
                jedis.close();
            }
        }
    }

    public RecordWriter<Text, Text> getRecordWriter(TaskAttemptContext context) throws IOException, InterruptedException {
        return new MySQLRecordWriter();
    }

    public void checkOutputSpecs(JobContext context) throws IOException, InterruptedException {

    }
    private FileOutputCommitter committer = null;
    public static Path getOutputPath(JobContext job) {
        String name = job.getConfiguration().get(FileOutputFormat.OUTDIR);
        return name == null ? null: new Path(name);
    }
    public OutputCommitter getOutputCommitter(TaskAttemptContext context) throws IOException, InterruptedException {
        if (committer == null) {
            Path output = getOutputPath(context);
            committer = new FileOutputCommitter(output, context);
        }
        return committer;
    }
}
