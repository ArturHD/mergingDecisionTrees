/*
 Copyright (c) 2013 by Artur Andrzejak <arturuni@gmail.com>, Felix Langner, Silvestre Zabala

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.


 */

package experiment

import cubes.ClassCube

import cubes.JoinAdjacentCubes
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionListener
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import trees.TreeFromBoxesBuilder
import weka.core.FastVector
import weka.core.Instances
import weka.filters.Filter
import weka.filters.unsupervised.instance.RemovePercentage
import java.awt.*
import javax.swing.*

import cubes.BuildTreeAndGetBoxSet
import cubes.BoxSet

/**
 * Created by IntelliJ IDEA.
 * User: flangner
 * Date: 13.03.12
 * Time: 13:49
 * To change this template use File | Settings | File Templates.
 */
@Typed
class Visualization {

    /** color for the font used in column and row names */
    private final Color fontColor = new Color(98, 101, 156);

    /** font used in column and row names */
    private final java.awt.Font f = new java.awt.Font("Dialog", java.awt.Font.BOLD, 11);

    private final double[] min = new double[2]
    private final double[] max = new double[2]
    private final double[] ratio = new double[2]

    private final int modelSize = 1000

    private Instances m_format
    private int attribute1 = 0
    private int attribute2 = 0

    /**
     * If this method is utilized properly, it will open a window with a visualization of the provided information
     * as 2D plot.
     *
     * @param instances
     * @param cubes
     */
    void visualize(Instances instances, Collection cubes) {
        visualize([instances].toArray(new Instances[1]), [cubes].toArray(new Collection[1]))
    }

    /**
     * If this method is utilized properly, it will open a window with a visualization of the provided information
     * as 2D plot.
     *
     * @param instances
     * @param cubeModels
     */
    void visualize(Instances[] instances, Collection[] cubeModels) {

        if (!m_format) throw new Exception("InputFormat has not been specified yet.")

        // build the frame
        final int size = 800

        final JFrame jf = new JFrame();


        jf.getContentPane().setLayout(new GridLayout((int) Math.ceil(Math.sqrt(cubeModels.length)),
                (int) Math.floor(Math.sqrt(cubeModels.length)), 5, 5));

        def layout = new ScrollPaneLayout()

        // add the models
        for (int i = 0; i < cubeModels.length; i++) {

            String file = "data/plot-${i}"


            BufferedImage img = new BufferedImage(modelSize, modelSize, BufferedImage.TYPE_4BYTE_ABGR_PRE)
            Plot p = new Plot(instances[i], cubeModels[i], attribute1, attribute2, modelSize, true)
            p.paintComponent(img.createGraphics())
            ImageIO.write(img, "png", new File("${file}.png"))
            // plotToFile(file, instances[i], cubeModels[i], true)

            JScrollPane pane = new JScrollPane(new ScrollablePicture(new ImageIcon(file + ".png"), 1))
            jf.getContentPane().add(pane)
        }

        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setSize(size, size);
        jf.setVisible(true);
        jf.repaint();
    }

    /**
     * Plots and saves the given cubes and instances to the given file, extended by the file format.
     *
     * @param path - without format file extension
     * @param cubes - to visualize
     * @param instances - describing the model given by the cubes
     * @param plotInstances - flag to determine whether the instances will be plotted along with the cubes.
     *                        true by default
     */
    void plotToFile(String path, Instances instances, Collection cubes, boolean plotInstances) {

        if (!m_format) throw new Exception("InputFormat has not been specified yet.")

        BufferedImage img = new BufferedImage(modelSize, modelSize, BufferedImage.TYPE_4BYTE_ABGR_PRE)
        Plot p = new Plot((instances) ? instances : m_format, cubes, attribute1, attribute2, modelSize, plotInstances)
        p.paintComponent(img.createGraphics())
        ImageIO.write(img, "png", new File("${path}.png"))
    }

    /**
     * @param instances
     * @param attribute1 - x dimension of the plot
     * @param attribute2 - y dimension of the plot
     */
    void setInputFormat(Instances instances, int attribute1, int attribute2) {

        assert instances: "Instances has to be defined!"

        this.m_format = instances
        this.attribute1 = attribute1
        this.attribute2 = attribute2

        parseFormat()
    }

    void parseFormat() {

        int[] selectAttribs = new int[2]
        selectAttribs[0] = attribute1
        selectAttribs[1] = attribute2

        for (int j = 0; j < selectAttribs.length; j++) {

            int i;
            // initialization of the min/max-values
            for (i = 0; i < m_format.numInstances(); i++) {

                min[j] = max[j] = 0;
                if (!(m_format.instance(i).isMissing(selectAttribs[j]))) {
                    min[j] = max[j] = m_format.instance(i).value(selectAttribs[j]);
                    break;
                }
            }

            for (i = 0; i < m_format.numInstances(); i++) {

                if (!(m_format.instance(i).isMissing(selectAttribs[j]))) {
                    if (m_format.instance(i).value(selectAttribs[j]) < min[j])
                        min[j] = m_format.instance(i).value(selectAttribs[j]);
                    if (m_format.instance(i).value(selectAttribs[j]) > max[j])
                        max[j] = m_format.instance(i).value(selectAttribs[j]);
                }
            }

            // add a small margin
            double margin = 1//modelSize / 300
            min[j] -= margin
            max[j] += margin

            ratio[j] = modelSize / (max[j] - min[j]);
        }
    }

    static Visualization visualizer() {
        return new Visualization()
    }

    /**
     * Method to test visualization-mechanics.
     *
     * @param args
     */
    static void main(String[] args) {

        String path = "data/arff/2DdataCircleGSev1Sp1Train.arff"
//        String path = "data/arff/2DTest.arff"
//        String path = "data/arff/cd_synthetic.arff"   // MISSING DATA FILE!
//        String path = "data/arff/2DCardiotocography.arff"
        Visualization visualizer = visualizer()

        final BuildTreeAndGetBoxSet builder = new BuildTreeAndGetBoxSet()
        final BoxSet[] models = new BoxSet[6]
        final Instances[] datas = new Instances[6]

        datas[2] = datas[3] = datas[4] = datas[5] = Tools.loadArff(path)
        visualizer.setInputFormat(datas[2], 0, 1)

        RemovePercentage filter = new RemovePercentage()
        filter.setInputFormat(datas[2])
        filter.setPercentage(50)
        datas[0] = Filter.useFilter(datas[2], filter)
        models[0] = builder.buildCubes(datas[0])

        filter = new RemovePercentage()
        filter.setInputFormat(datas[2])
        filter.setPercentage(50)
        filter.setInvertSelection(true)
        datas[1] = Filter.useFilter(datas[2], filter)
        models[1] = builder.buildCubes(datas[1])

        BoxSet boxSet = new BoxSet()
        boxSet.mergeBoxSetsViaIntersections(models[0])
        boxSet.mergeBoxSetsViaIntersections(models[1])
        models[2] = boxSet

        boxSet = new BoxSet()
        boxSet.mergeBoxSetsViaIntersections(models[0])
        boxSet.mergeBoxSetsViaIntersections(models[1])
        models[3] = new JoinAdjacentCubes(boxSet).joinAdjacentCubes()

        // def treeBuilder = TreeFromBoxesBuilder.create(models[3], datas[3], true)  // AA: original code before 16.06.2012
        def treeBuilder = TreeFromBoxesBuilder.create(models[3], datas[3], 2)   // last arg  is pruningType
        treeBuilder.buildTree()
        models[4] = treeBuilder.getBoxSet()

        models[5] = builder.buildCubes(datas[5])

        visualizer.visualize(datas, models)
        def explanations = "\n #1: boxes from D_0, #2: boxes from D_1\n #3: intersected boxes, #4: joined adjacent boxes\n #5: after tree pruning, #6: boxes on all data"

        println "========== Explanations: $explanations"
    }

    /**
     * @since 03/13/2012
     * modified version of weka.gui.visualize.MatrixPanel.Plot of Weka 3.6.6.
     *
     Internal class responsible for displaying the actual matrix
     Requires the internal data fields of the parent class to be properly initialized
     before being created
     */
    private class Plot extends JComponent {

        /** for serialization */
        private static final long serialVersionUID = -1721245738439420882L;

        /** Contains discrete colours for colouring for nominal attributes */
        private final FastVector m_colorList = new FastVector();

        /** default colour list */
        private final Color[] m_defaultColors = [Color.blue,
                Color.red,
                Color.cyan,
                new Color(75, 123, 130),
                Color.pink,
                Color.green,
                Color.orange,
                new Color(255, 0, 255),
                new Color(255, 0, 0),
                new Color(0, 255, 0),
                Color.black];

        java.awt.FontMetrics fm

        private final Instances m_data;
        private final int[] m_selectedAttribs;
        /** This is a local array cache for all the instance values for faster rendering */
        private final int[][] m_points;

        /** This is an array cache for the colour of each of the instances depending on the
         colouring attribute. If the colouring attribute is nominal then it contains the
         index of the colour in our colour list. Otherwise, for numeric colouring attribute,
         it contains the precalculated red component for each instance's colour */
        private final int[] m_pointColors;

        private final Rectangle[] m_cubes;
        private final int[] m_cubeColors;
        private final double[] m_cubeColorIntensity;

        /** Constructor
         */
        public Plot(Instances instances, Collection cubes, int x, int y, int size, boolean plotInstances) {
            super();

            assert instances
            assert cubes

            // initialize local fields
            m_data = instances

            m_selectedAttribs = new int[2]
            m_selectedAttribs[0] = x
            m_selectedAttribs[1] = y

            // pre-process instances
            // coordinates
            if (plotInstances) {
                m_points = new int[m_data.numInstances()][m_selectedAttribs.length]
                for (int j = 0; j < m_selectedAttribs.length; j++) {

                    for (int i = 0; i < m_data.numInstances(); i++) {

                        m_points[i][j] = (int) Math.round((m_data.instance(i).value(m_selectedAttribs[j]) - min[j]) * ratio[j])
                    }
                }
            } else {
                m_points = new int[0][0]
            }

            /** Setting up the initial color list **/
            for (int i = 0; i < m_defaultColors.length - 1; i++)
                m_colorList.addElement(m_defaultColors[i]);

            // colors
            /** Setting up the color list for non-numeric attribute**/
            final int classIndex = m_data.classIndex()
            double minC = 0, maxC = 0;
            m_pointColors = new int[m_data.numInstances()];
            if (!(m_data.attribute(classIndex).isNumeric())) {


                for (int i = 0; i < m_data.numInstances(); i++) {

                    m_pointColors[i] = (int) m_data.instance(i).value(classIndex);
                }
            } else {

                for (int i = 1; i < m_data.numInstances(); i++) {
                    if (!(m_data.instance(i).isMissing(classIndex))) {
                        if (minC > m_data.instance(i).value(classIndex))
                            minC = m_data.instance(i).value(classIndex);
                        if (maxC < m_data.instance(i).value(classIndex))
                            maxC = m_data.instance(i).value(classIndex);
                    }
                }

                for (int i = 0; i < m_data.numInstances(); i++) {
                    double r = (m_data.instance(i).value(classIndex) - minC) / (maxC - minC);
                    r = (r * 240) + 15;
                    m_pointColors[i] = (int) r;
                }
            }

            // pre-process boxes
            m_cubeColors = new int[cubes.size()]
            m_cubes = new Rectangle[cubes.size()]
            m_cubeColorIntensity = new double[cubes.size()]

            int index = 0
            for (ClassCube cube: cubes) {

                final int lowerX = Math.max(Math.round((cube.getLower(x) - min[x]) * ratio[x]), 0)
                final int upperX = Math.min(Math.round((cube.getUpper(x) - min[x]) * ratio[x]), modelSize - 1)
                final int lowerY = Math.max(Math.round((cube.getLower(y) - min[y]) * ratio[y]), 0)
                final int upperY = Math.min(Math.round((cube.getUpper(y) - min[y]) * ratio[y]), modelSize - 1)
                m_cubes[index] = new Rectangle(lowerX, lowerY, upperX - lowerX, upperY - lowerY)

                if (cube.classData.hasConflict) {

                    m_cubeColors[index] = -1
                } else if (!(m_data.attribute(classIndex).isNumeric())) {

                    m_cubeColors[index] = cube.classValue;
                } else {

                    double r = (cube.classValue - minC) / (maxC - minC);
                    r = (r * 240) + 15;
                    m_cubeColors[index] = (int) r;
                }

                m_cubeColorIntensity[index] = cube.classData.getClassProbDistribution()[(int) cube.classValue]

                index++
            }
        }

        /**  Paints a single Plot at xpos, ypos. and xattrib and yattrib on X and
         Y axes
         */
        public void paintGraph(Graphics g, int xattrib, int yattrib) {

            int x, y;
            g.setColor(Color.white);
            g.fillRect(0, 0, modelSize, modelSize);
            final int classIndex = m_data.classIndex()

            // cubes
            for (int i = 0; i < m_cubes.length; i++) {

                // set color
                Color color;
                if (m_cubeColors[i] < 0)
                    color = Color.black
                else if (!(m_data.attribute(classIndex).isNumeric()))
                    color = (Color) m_colorList.elementAt(m_cubeColors[i])
                else
                    color = new Color(m_pointColors[i], 150, (255 - m_pointColors[i]))

                // draw shape
                g.setColor(color)

                int cx = m_cubes[i].x
                int cy = m_cubes[i].y
                // a width or height is not permitted
                int width = Math.max(m_cubes[i].width, 1)
                int height = Math.max(m_cubes[i].height, 1)
                g.drawRect(cx, cy, width, height)

                color = new Color(color.red, color.green, color.blue, (int) (50 * m_cubeColorIntensity[i]))
                g.setColor(color)
                g.fillRect(cx, cy, width, height)
            }

            // samples
            for (int i = 0; i < m_points.length; i++) {

                // set color
                if (!(m_data.attribute(classIndex).isNumeric()))
                    g.setColor((Color) m_colorList.elementAt(m_pointColors[i]))
                else
                    g.setColor(new Color(m_pointColors[i], 150, (255 - m_pointColors[i])))

                // draw point
                x = m_points[i][xattrib];
                y = (modelSize - m_points[i][yattrib]);
                g.drawOval(x, y, 1, 1)
            }

            g.setColor(fontColor);
        }

        /**
         Paints the matrix of plots in the current visible region
         */
        public void paintME(Graphics g) {
            g.setColor(getBackground());
            g.fillRect(0, 0, modelSize, modelSize);
            g.setColor(fontColor);

            paintGraph(g, m_selectedAttribs[0], m_selectedAttribs[1]);
        }

        /** paints this JComponent (Plot)
         */
        public void paintComponent(Graphics g) {

            assert (g)

            paintME(g);
        }
    }

    private class ScrollablePicture extends JLabel implements Scrollable, MouseMotionListener {

        private int maxUnitIncrement = 1;
        private boolean missingPicture = false;

        public ScrollablePicture(ImageIcon i, int m) {
            super(i);
            if (i == null) {
                missingPicture = true;
                setText("No picture found.");
                setHorizontalAlignment(CENTER);
                setOpaque(true);
                setBackground(Color.white);
            }
            maxUnitIncrement = m;

            //Let the user scroll by dragging to outside the window.
            setAutoscrolls(true); //enable synthetic drag events
            addMouseMotionListener(this); //handle mouse drags
        }

        //Methods required by the MouseMotionListener interface:
        void mouseMoved(MouseEvent e) { }

        void mouseDragged(MouseEvent e) {
            //The user is dragging us, so scroll!
            Rectangle r = new Rectangle(e.getX(), e.getY(), 1, 1);
            scrollRectToVisible(r);
        }

        Dimension getPreferredSize() {
            if (missingPicture) {
                return new Dimension(320, 480);
            } else {
                return super.getPreferredSize();
            }
        }

        Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        int getScrollableUnitIncrement(Rectangle visibleRect,
                                       int orientation,
                                       int direction) {
            //Get the current position.
            int currentPosition;
            if (orientation == SwingConstants.HORIZONTAL) {
                currentPosition = visibleRect.x;
            } else {
                currentPosition = visibleRect.y;
            }

            //Return the number of pixels between currentPosition
            //and the nearest tick mark in the indicated direction.
            if (direction < 0) {
                int newPosition = currentPosition - (currentPosition / maxUnitIncrement) * maxUnitIncrement;
                return (newPosition == 0) ? maxUnitIncrement : newPosition;
            } else {
                return ((currentPosition / maxUnitIncrement) + 1) * maxUnitIncrement - currentPosition;
            }
        }

        int getScrollableBlockIncrement(Rectangle visibleRect,
                                        int orientation,
                                        int direction) {
            if (orientation == SwingConstants.HORIZONTAL) {
                return visibleRect.width - maxUnitIncrement;
            } else {
                return visibleRect.height - maxUnitIncrement;
            }
        }

        boolean getScrollableTracksViewportWidth() {
            return false;
        }

        boolean getScrollableTracksViewportHeight() {
            return false;
        }

        void setMaxUnitIncrement(int pixels) {
            maxUnitIncrement = pixels;
        }
    }
}
