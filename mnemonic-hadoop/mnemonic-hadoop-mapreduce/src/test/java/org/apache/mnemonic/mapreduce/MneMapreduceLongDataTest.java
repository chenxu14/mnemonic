/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.mnemonic.mapreduce;

import java.io.File;
import java.io.IOException;
import java.util.Random;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapreduce.InputFormat;
import org.apache.hadoop.mapreduce.OutputFormat;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.RecordWriter;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.TaskAttemptID;
import org.apache.hadoop.mapreduce.TaskType;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.mapreduce.task.TaskAttemptContextImpl;
import org.apache.mnemonic.DurableType;
import org.apache.mnemonic.Utils;
import org.apache.mnemonic.hadoop.MneConfigHelper;
import org.apache.mnemonic.hadoop.MneDurableInputValue;
import org.apache.mnemonic.hadoop.MneDurableOutputSession;
import org.apache.mnemonic.hadoop.MneDurableOutputValue;
import org.apache.mnemonic.hadoop.mapreduce.MneInputFormat;
import org.apache.mnemonic.hadoop.mapreduce.MneOutputFormat;
import org.testng.AssertJUnit;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class MneMapreduceLongDataTest {

  private static final String SERVICE_NAME = "pmalloc";
  private static final long SLOT_KEY_ID = 7L;
  private Path m_workdir;
  private JobConf m_conf;
  private FileSystem m_fs;
  private Random m_rand;
  private TaskAttemptID m_taid;
  private TaskAttemptContext m_tacontext;
  private long m_reccnt = 500000L;
  private long m_sum = 0L;

  @BeforeClass
  public void setUp() throws IOException {
    m_workdir = new Path(
        System.getProperty("test.tmp.dir", "target" + File.separator + "test" + File.separator + "tmp"));
    m_conf = new JobConf();
    m_rand = Utils.createRandom();

    try {
      m_fs = FileSystem.getLocal(m_conf).getRaw();
      m_fs.delete(m_workdir, true);
      m_fs.mkdirs(m_workdir);
    } catch (IOException e) {
      throw new IllegalStateException("bad fs init", e);
    }

    m_taid = new TaskAttemptID("jt", 0, TaskType.MAP, 0, 0);
    m_tacontext = new TaskAttemptContextImpl(m_conf, m_taid);

    m_conf.set("mapreduce.output.fileoutputformat.outputdir", m_workdir.toString());
    MneConfigHelper.setBaseOutputName(m_conf, null, "long-data");

    MneConfigHelper.setMemServiceName(m_conf, MneConfigHelper.DEFAULT_INPUT_CONFIG_PREFIX, SERVICE_NAME);
    MneConfigHelper.setSlotKeyId(m_conf, MneConfigHelper.DEFAULT_INPUT_CONFIG_PREFIX, SLOT_KEY_ID);
    MneConfigHelper.setDurableTypes(m_conf,
        MneConfigHelper.DEFAULT_INPUT_CONFIG_PREFIX, new DurableType[] {DurableType.LONG});
    MneConfigHelper.setEntityFactoryProxies(m_conf,
        MneConfigHelper.DEFAULT_INPUT_CONFIG_PREFIX, new Class<?>[] {});
    MneConfigHelper.setMemServiceName(m_conf, MneConfigHelper.DEFAULT_OUTPUT_CONFIG_PREFIX, SERVICE_NAME);
    MneConfigHelper.setSlotKeyId(m_conf, MneConfigHelper.DEFAULT_OUTPUT_CONFIG_PREFIX, SLOT_KEY_ID);
    MneConfigHelper.setMemPoolSize(m_conf,
        MneConfigHelper.DEFAULT_OUTPUT_CONFIG_PREFIX, 1024L * 1024 * 1024 * 2);
    MneConfigHelper.setDurableTypes(m_conf,
        MneConfigHelper.DEFAULT_OUTPUT_CONFIG_PREFIX, new DurableType[] {DurableType.LONG});
    MneConfigHelper.setEntityFactoryProxies(m_conf,
        MneConfigHelper.DEFAULT_OUTPUT_CONFIG_PREFIX, new Class<?>[] {});
  }

  @AfterClass
  public void tearDown() {

  }

  @Test(enabled = true)
  public void testWriteLongData() throws Exception {
    NullWritable nada = NullWritable.get();
    MneDurableOutputSession<Long> sess = new MneDurableOutputSession<Long>(m_tacontext);
    sess.readConfig(MneConfigHelper.DEFAULT_OUTPUT_CONFIG_PREFIX);
    sess.initNextPool();
    MneDurableOutputValue<Long> mdvalue =
        new MneDurableOutputValue<Long>(sess);
    OutputFormat<NullWritable, MneDurableOutputValue<Long>> outputFormat =
        new MneOutputFormat<MneDurableOutputValue<Long>>();
    RecordWriter<NullWritable, MneDurableOutputValue<Long>> writer =
        outputFormat.getRecordWriter(m_tacontext);
    Long val = null;
    for (int i = 0; i < m_reccnt; ++i) {
      val = m_rand.nextLong();
      m_sum += val;
      writer.write(nada, mdvalue.of(val));
    }
    writer.close(m_tacontext);
    sess.close();
  }

  @Test(enabled = true, dependsOnMethods = { "testWriteLongData" })
  public void testReadLongData() throws Exception {
    long sum = 0L;
    long reccnt = 0L;
    File folder = new File(m_workdir.toString());
    File[] listfiles = folder.listFiles();
    for (int idx = 0; idx < listfiles.length; ++idx) {
      if (listfiles[idx].isFile()
          && listfiles[idx].getName().startsWith(MneConfigHelper.getBaseOutputName(m_conf, null))
          && listfiles[idx].getName().endsWith(MneConfigHelper.DEFAULT_FILE_EXTENSION)) {
        System.out.println(String.format("Verifying : %s", listfiles[idx].getName()));
        FileSplit split = new FileSplit(
            new Path(m_workdir, listfiles[idx].getName()), 0, 0L, new String[0]);
        InputFormat<NullWritable, MneDurableInputValue<Long>> inputFormat =
            new MneInputFormat<MneDurableInputValue<Long>, Long>();
        RecordReader<NullWritable, MneDurableInputValue<Long>> reader =
            inputFormat.createRecordReader(split, m_tacontext);
        MneDurableInputValue<Long> mdval = null;
        while (reader.nextKeyValue()) {
          mdval = reader.getCurrentValue();
          sum += mdval.getValue();
          ++reccnt;
        }
        reader.close();
      }
    }
    AssertJUnit.assertEquals(m_sum, sum);
    AssertJUnit.assertEquals(m_reccnt, reccnt);
    System.out.println(String.format("The checksum of long data is %d", sum));
  }
}
