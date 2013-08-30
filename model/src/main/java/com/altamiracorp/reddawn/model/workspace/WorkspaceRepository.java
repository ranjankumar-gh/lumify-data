package com.altamiracorp.reddawn.model.workspace;

import com.altamiracorp.reddawn.model.Column;
import com.altamiracorp.reddawn.model.ColumnFamily;
import com.altamiracorp.reddawn.model.Repository;
import com.altamiracorp.reddawn.model.Row;

import java.util.Collection;

public class WorkspaceRepository extends Repository<Workspace> {
    @Override
    public Workspace fromRow(Row row) {
        Workspace artifact = new Workspace(row.getRowKey());
        Collection<ColumnFamily> families = row.getColumnFamilies();
        for (ColumnFamily columnFamily : families) {
            String columnFamilyName = columnFamily.getColumnFamilyName();
            if (columnFamilyName.equals(WorkspaceContent.NAME)) {
                Collection<Column> columns = columnFamily.getColumns();
                artifact.addColumnFamily(new WorkspaceContent().addColumns(columns));
            } else if (columnFamilyName.equals(WorkspaceMetadata.NAME)) {
                Collection<Column> columns = columnFamily.getColumns();
                artifact.addColumnFamily(new WorkspaceMetadata().addColumns(columns));
            } else {
                artifact.addColumnFamily(columnFamily);
            }
        }
        return artifact;
    }

    @Override
    public Row toRow(Workspace workspace) {
        return workspace;
    }

    @Override
    public String getTableName() {
        return Workspace.TABLE_NAME;
    }
}
