package de.otto.jobstore.repository;

import com.mongodb.*;
import de.otto.jobstore.common.*;
import de.otto.jobstore.common.properties.JobDefinitionProperty;

import java.util.Date;


public class JobDefinitionRepository extends AbstractRepository<StoredJobDefinition> {

    /**
     * @deprecated Please use {@link #JobDefinitionRepository(MongoClient, String, String)} instead}
     */
    @Deprecated
    public JobDefinitionRepository(Mongo mongo, String dbName, String collectionName) {
        super(createMongoClient(mongo, dbName, null, null), dbName, collectionName);
    }

    /**
     * @deprecated Please use {@link #JobDefinitionRepository(MongoClient, String, String)} instead}
     */
    @Deprecated
    public JobDefinitionRepository(Mongo mongo, String dbName, String collectionName, String username, String password) {
        super(createMongoClient(mongo, dbName, username, password), dbName, collectionName);
    }

    /**
     * @deprecated Please use {@link #JobDefinitionRepository(MongoClient, String, String, WriteConcern)} instead}
     */
    @Deprecated
    public JobDefinitionRepository(Mongo mongo, String dbName, String collectionName, String username, String password, WriteConcern safeWriteConcern) {
        super(createMongoClient(mongo, dbName, username, password), dbName, collectionName, safeWriteConcern);
    }

    public JobDefinitionRepository(MongoClient mongo, String dbName, String collectionName) {
        super(mongo, dbName, collectionName);
    }

    public JobDefinitionRepository(MongoClient mongo, String dbName, String collectionName, WriteConcern safeWriteConcern) {
        super(mongo, dbName, collectionName, safeWriteConcern);
    }


    public StoredJobDefinition find(String name) {
        final DBObject object = collection.findOne(new BasicDBObject(JobDefinitionProperty.NAME.val(), name));
        return fromDbObject(object);
    }

    @Override
    protected void prepareCollection() {
        collection.createIndex(new BasicDBObject(JobDefinitionProperty.NAME.val(), 1), "name", true);
    }

    @Override
    protected StoredJobDefinition fromDbObject(DBObject dbObject) {
        if (dbObject == null) {
            return null;
        }
        return new StoredJobDefinition(dbObject);
    }

    public void addOrUpdate(StoredJobDefinition jobDefinition) {
        final DBObject obj = new BasicDBObject(MongoOperator.SET.op(), buildUpdateObject(jobDefinition));
        collection.update(new BasicDBObject(JobDefinitionProperty.NAME.val(), jobDefinition.getName()), obj, true, false, getSafeWriteConcern());
    }

    private BasicDBObject buildUpdateObject(StoredJobDefinition jobDefinition) {
        final BasicDBObject basicDBObject = new BasicDBObject();
        final DBObject jobDefObj = jobDefinition.toDbObject();
        for (JobDefinitionProperty property : JobDefinitionProperty.values()) {
            if (!property.isDynamic()) {
                basicDBObject.append(property.val(), jobDefObj.get(property.val()));
            }
        }
        return basicDBObject;
    }

    public void setJobExecutionEnabled(String name, boolean executionEnabled) {
        collection.update(new BasicDBObject(JobDefinitionProperty.NAME.val(), name),
                new BasicDBObject(MongoOperator.SET.op(), new BasicDBObject(JobDefinitionProperty.DISABLED.val(), !executionEnabled)));
    }

    public void setLastNotExecuted(String name, Date date) {
        collection.update(new BasicDBObject(JobDefinitionProperty.NAME.val(), name),
                new BasicDBObject(MongoOperator.SET.op(), new BasicDBObject(JobDefinitionProperty.LAST_NOT_EXECUTED.val(), date)));
    }

}
