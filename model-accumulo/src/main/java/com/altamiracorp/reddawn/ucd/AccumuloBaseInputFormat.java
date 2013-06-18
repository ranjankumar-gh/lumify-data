package com.altamiracorp.reddawn.ucd;

import com.altamiracorp.reddawn.model.AccumuloHelper;
import com.altamiracorp.reddawn.model.Repository;
import com.altamiracorp.reddawn.model.Row;
import org.apache.accumulo.core.client.RowIterator;
import org.apache.accumulo.core.client.mapreduce.InputFormatBase;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.util.PeekingIterator;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

import java.io.IOException;
import java.util.Map;

public abstract class AccumuloBaseInputFormat<TModel extends Row, TRepo extends Repository<TModel>> extends InputFormatBase<Text, TModel> {

    public abstract TRepo getRepository();

    @Override
    public RecordReader<Text, TModel> createRecordReader(InputSplit split, TaskAttemptContext context) throws IOException, InterruptedException {
        return new RecordReaderBase<Text, TModel>() {
            RowIterator rowIterator;

            @Override
            public void initialize(InputSplit inSplit, TaskAttemptContext attempt) throws IOException {
                super.initialize(inSplit, attempt);
                this.rowIterator = new RowIterator(scannerIterator);
                this.currentK = new Text();
                this.currentV = null;
            }

            @Override
            public boolean nextKeyValue() throws IOException, InterruptedException {
                if (!rowIterator.hasNext()) {
                    return false;
                }
                PeekingIterator<Map.Entry<Key, Value>> it = new PeekingIterator<Map.Entry<Key, Value>>(rowIterator.next());
                TRepo repository = getRepository();
                this.currentV = repository.fromRow(AccumuloHelper.accumuloRowToRow(repository.getTableName(), it));
                this.numKeysRead = this.rowIterator.getKVCount();
                this.currentKey = new Key(this.currentV.getRowKey().toString());
                this.currentK = new Text(this.currentKey.getRow());
                return true;
            }
        };
    }
}
