package org.sonatype.nexus.blobstore.azure.internal.db;

import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.sonatype.nexus.blobstore.api.BlobId;
import org.sonatype.nexus.blobstore.azure.internal.DeletedBlobIndex;
import org.sonatype.nexus.common.app.ManagedLifecycle;
import org.sonatype.nexus.common.app.ManagedLifecycle.Phase;
import org.sonatype.nexus.common.stateguard.StateGuardLifecycleSupport;
import org.sonatype.nexus.orient.DatabaseInstance;
import org.sonatype.nexus.orient.transaction.OrientOperations;
import org.sonatype.nexus.transaction.Operations;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.StreamSupport.stream;
import static org.sonatype.nexus.orient.DatabaseInstanceNames.COMPONENT;
import static org.sonatype.nexus.orient.transaction.OrientTransactional.inTxRetry;
import static org.sonatype.nexus.orient.transaction.OrientTransactional.operation;

/**
 * OrientDB implementation for the {@link DeletedBlobIndex}. This will leverage the NXRM database to track deleted
 * blob references (soft-deletes)
 */
@Named(OrientDeletedBlobIndex.NAME)
@Singleton
@ManagedLifecycle(phase = Phase.SCHEMAS)
public class OrientDeletedBlobIndex
    extends StateGuardLifecycleSupport
    implements DeletedBlobIndex
{
  public static final String NAME = "orientDeletedBlobIndex";

  private final OrientDeletedBlobEntityAdapter adapter;

  private Provider<DatabaseInstance> database;

  @Inject
  public OrientDeletedBlobIndex(final OrientDeletedBlobEntityAdapter adapter,
                                @Named(COMPONENT) final Provider<DatabaseInstance> database)
  {
    this.adapter = checkNotNull(adapter);
    this.database = checkNotNull(database);
  }

  @Override
  protected void doStart() throws Exception {
    super.doStart();

    try (ODatabaseDocumentTx db = database.get().connect()) {
      adapter.register(db);
    }
  }

  @Override
  public void add(final BlobId blobId) {
    BlobIdEntity blobIdEntity = adapter.newEntity();
    blobIdEntity.setBlobId(blobId.asUniqueString());

    inTxRetry(database).run(db -> adapter.addEntity(db, blobIdEntity));
  }

  @Override
  public void remove(final BlobId blobId) {
    inTxRetry(database).run((db) -> adapter.delete(db, blobId));
  }

  @Override
  public Stream<BlobId> browse() {
    return inTxRetry(database)
        .call(db -> stream(adapter.browse(db).spliterator(), false).map(entity -> new BlobId(entity.getBlobId())));
  }
}
