/*
 *  The MIT License
 *
 *  Copyright 2011 Praqma A/S.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */

package net.praqma.jenkins.plugin.reloaded;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import net.praqma.jenkins.plugin.reloaded.MatrixReloadedState.BuildState;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import hudson.matrix.Combination;
import hudson.matrix.MatrixRun;
import hudson.matrix.MatrixBuild;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Hudson;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;

/**
 * The Matrix Reloaded Action class. This enables the plugin to add the link
 * action to the side panel.
 * 
 * @author wolfgang
 */
public class MatrixReloadedAction implements Action {
    private AbstractBuild<?, ?> build;

    private String checked = null;

    private static final Logger logger = Logger.getLogger(MatrixReloadedAction.class.getName());

    enum BuildType {
        MATRIXBUILD, MATRIXRUN, UNKNOWN
    }

    public MatrixReloadedAction() {
    }

    public MatrixReloadedAction(String checked) {
        this.checked = checked;
    }

    public String getDisplayName() {
        return Definitions.__DISPLAY_NAME;
    }

    public String getIconFileName() {
        return Definitions.__ICON_FILE_NAME;
    }

    public String getUrlName() {
        return Definitions.__URL_NAME;
    }

    public AbstractBuild<?, ?> getBuild() {
        return build;
    }

    public String getPrefix() {
        return Definitions.__PREFIX;
    }

    public String getChecked() {
        return this.checked;
    }

    public void performConfig(AbstractBuild<?, ?> build, JSONObject formData) {
        List<ParameterValue> values = new ArrayList<ParameterValue>();

        Iterator<?> it = formData.keys();

        logger.info("[MRP] The MATRIX RELOADED FORM has been submitted");
        logger.fine(formData.toString(2));

        /* UUID */
        String uuid = build.getProject().getDisplayName() + "_" + build.getNumber() + "_"
                + System.currentTimeMillis();
        BuildState bs = MatrixReloadedState.getInstance().getBuildState(uuid);

        logger.fine("UUID given: " + uuid);

        /* Generate the parameters */
        while (it.hasNext()) {
            String key = (String)it.next();

            /* Check the fields of the form */
            if (key.startsWith(Definitions.__PREFIX)) {
                String[] vs = key.split(Definitions.__DELIMITER, 2);
                try {
                    boolean checked = formData.getBoolean(key);

                    boolean rebuild = false;

                    /**/
                    if (vs.length > 1 && checked) {
                        rebuild = true;
                    }

                    /* Add parameter to the build state */
                    if (vs.length > 1) {
                        bs.addConfiguration(Combination.fromString(vs[1]), rebuild);
                    }
                } catch (JSONException e) {
                    /* No-op, not the field we were looking for. */
                }
            }

            /*
             * The special form field, providing information about the build we
             * decent from
             */
            if (key.equals(Definitions.__PREFIX + "NUMBER")) {
                String value = formData.getString(key);
                try {
                    bs.rebuildNumber = Integer.parseInt(value);
                } catch (NumberFormatException w) {
                    /*
                     * If we can't parse the integer, the number is zero. This
                     * will either make the new run fail or rebuild it id
                     * rebuildIfMissing is set(not set actually)
                     */
                    bs.rebuildNumber = 0;
                }
            }
        }

        /* Get the parameters of the build, if any and add them to the build */
        ParametersAction actions = build.getAction(ParametersAction.class);
        if (actions != null) {
            List<ParameterValue> list = actions.getParameters();
            for (ParameterValue pv : list) {
                // if( !pv.getName().startsWith( Definitions.prefix ) )
                if (!pv.getName().equals(Definitions.__UUID)) {
                    values.add(pv);
                }
            }
        }

        /* Add the UUID to the new build. */
        values.add(new StringParameterValue(Definitions.__UUID, uuid));

        /* Schedule the MatrixBuild */
        Hudson.getInstance()
                .getQueue()
                .schedule(build.getProject(), 0, new ParametersAction(values),
                        new CauseAction(new Cause.UserCause()));

    }

    public void doConfigSubmit(StaplerRequest req, StaplerResponse rsp) throws ServletException,
            IOException, InterruptedException {
        AbstractBuild<?, ?> mbuild = req.findAncestorObject(AbstractBuild.class);
        AbstractBuild<?, ?> build = null;

        BuildType type;

        if (req.findAncestor(MatrixBuild.class) != null) {
            type = BuildType.MATRIXBUILD;
            build = mbuild;
        } else if (req.findAncestor(MatrixRun.class) != null) {
            type = BuildType.MATRIXRUN;
            build = ((MatrixRun)mbuild).getParentBuild();
        } else {
            type = BuildType.UNKNOWN;
        }

        JSONObject formData = req.getSubmittedForm();
        System.out.println(formData.toString(2));
        performConfig(build, formData);

        /*
         * Depending on where the form was submitted, the number of levels to
         * direct
         */
        if (type.equals(BuildType.MATRIXRUN)) {
            rsp.sendRedirect("../../../");
        } else {
            rsp.sendRedirect("../../");
        }
    }

}