package org.neo4j.graphalgo.impl;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.GraphFactory;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.core.graphbuilder.GraphBuilder;
import org.neo4j.graphalgo.core.graphbuilder.GridBuilder;
import org.neo4j.graphalgo.core.heavyweight.HeavyGraphFactory;
import org.neo4j.graphalgo.core.huge.HugeGraphFactory;
import org.neo4j.graphalgo.core.leightweight.LightGraphFactory;
import org.neo4j.graphalgo.core.neo4jview.GraphViewFactory;
import org.neo4j.graphalgo.core.utils.ParallelExporter;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.core.utils.ProgressTimer;
import org.neo4j.graphalgo.exporter.IntArrayExporter;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.FormattedLog;
import org.neo4j.logging.Level;
import org.neo4j.test.TestGraphDatabaseFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.TimeZone;
import java.util.function.Supplier;

import static org.junit.Assert.assertTrue;

/**
 * @author mknblch
 */
@RunWith(Parameterized.class)
public class ProgressLoggingTest {

    private static final String PROPERTY = "property";
    private static final String LABEL = "Node";
    private static final String RELATIONSHIP = "REL";

    private static GraphDatabaseAPI db;

    @Parameterized.Parameters(name = "{1}")
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[]{HeavyGraphFactory.class, "HeavyGraphFactory"},
//                new Object[]{LightGraphFactory.class, "LightGraphFactory"}, // doesn't log yet
//                new Object[]{GraphViewFactory.class, "GraphViewFactory"}, // doesn't log yet
                new Object[]{HugeGraphFactory.class, "HugeGraphFactory"}
        );
    }

    private Class<? extends GraphFactory> graphImpl;
    private Graph graph;

    @BeforeClass
    public static void setup() throws Exception {

        db = (GraphDatabaseAPI)
                new TestGraphDatabaseFactory()
                        .newImpermanentDatabaseBuilder()
                        .newGraphDatabase();

        try (ProgressTimer timer = ProgressTimer.start(t -> System.out.println("setup took " + t + "ms"))) {
            final GridBuilder gridBuilder = GraphBuilder.create(db)
                    .setLabel(LABEL)
                    .setRelationship(RELATIONSHIP)
                    .newGridBuilder()
                    .createGrid(100, 10)
                    .forEachRelInTx(rel -> {
                        rel.setProperty(PROPERTY, Math.random() * 5); // (0-5)
                    });
        }
    }

    @AfterClass
    public static void tearDown() {
        db.shutdown();
    }

    public ProgressLoggingTest(Class<? extends GraphFactory> graphImpl, String nameIgnored) {
        this.graphImpl = graphImpl;
        graph = new GraphLoader(db)
                .withExecutorService(Pools.DEFAULT)
                .withLabel(LABEL)
                .withRelationshipType(RELATIONSHIP)
                .withRelationshipWeightsFromProperty(PROPERTY, 1.0)
                .load(graphImpl);
    }

    @Test
    public void testLoad() throws Exception {

        final StringBuffer buffer = new StringBuffer();

        try (ProgressTimer timer = ProgressTimer.start(t -> System.out.println("load took " + t + "ms"))) {
            graph = new GraphLoader(db)
                    .withLog(new TestLogger(buffer))
                    .withExecutorService(Pools.DEFAULT)
                    .withLabel(LABEL)
                    .withRelationshipType(RELATIONSHIP)
                    .withRelationshipWeightsFromProperty(PROPERTY, 1.0)
                    .load(graphImpl);
        }

        System.out.println(buffer);

        final String output = buffer.toString();

        assertTrue(output.length() > 0);
        assertTrue(output.contains(GraphFactory.TASK_LOADING));
    }

    @Test
    public void testWrite() throws Exception {

        final StringBuffer buffer = new StringBuffer();

        final int[] ints = new int[graph.nodeCount()];
        Arrays.fill(ints, -1);

        new IntArrayExporter(db, graph, new TestLogger(buffer), "test", Pools.DEFAULT)
                .write(ints);

        System.out.println(buffer);

        final String output = buffer.toString();

        assertTrue(output.length() > 0);
        assertTrue(output.contains(ParallelExporter.TASK_EXPORT));
    }


    public static class TestLogger extends FormattedLog {

        private static class StreamBuffer extends OutputStream {

            private final StringBuffer buffer;

            private StreamBuffer(StringBuffer buffer) {
                this.buffer = buffer;
            }

            @Override
            public void write(int b) throws IOException {
                buffer.append((char) b);
            }
        }

        public TestLogger(StringBuffer buffer) {
            this(
                    Date::new,
                    () -> new PrintWriter(new StreamBuffer(buffer)),
                    TimeZone.getDefault(),
                    new Object(),
                    "Test",
                    Level.DEBUG,
                    true
            );
        }

        protected TestLogger(Supplier<Date> currentDateSupplier, Supplier<PrintWriter> writerSupplier, TimeZone timezone, Object maybeLock, String category, Level level, boolean autoFlush) {
            super(currentDateSupplier, writerSupplier, timezone, maybeLock, category, level, autoFlush);
        }
    }

}
