/*
 * Copyright 2005 Joe Walker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.directwebremoting.create;

import java.io.File;
import java.io.RandomAccessFile;

import javax.servlet.ServletContext;

import org.apache.bsf.BSFException;
import org.apache.bsf.BSFManager;
import org.directwebremoting.WebContext;
import org.directwebremoting.WebContextFactory;
import org.directwebremoting.extend.Creator;
import org.directwebremoting.util.LocalUtil;
import org.directwebremoting.util.Logger;
import org.directwebremoting.util.Messages;

/**
 * A creator that uses BeanShell to evaluate some script to create an object.
 * @author Joe Walker [joe at getahead dot ltd dot uk]
 * @author Dennis [devel at muhlesteins dot com]
 */
public class ScriptedCreator extends AbstractCreator implements Creator
{
    /**
     * Set up some defaults
     */
    public ScriptedCreator()
    {
        // The default is <b>true</b>. This parameter is only used if scriptPath is
        // used instead of script.  When reloadable is true, ScriptedCreator will
        // check to see if the script has been modified before returning the
        // existing created class.
        setCacheable(false);
    }

    /**
     * The language that we are scripting in. Passed to BSF.
     * @return Returns the language.
     */
    public String getLanguage()
    {
        return language;
    }

    /**
     * @param language The language to set.
     */
    public void setLanguage(String language)
    {
        // It would be good to be ablt to check this, but this seems to fail
        // almost all the time
        // if (BSFManager.isLanguageRegistered(language))
        // {
        //     throw new IllegalArgumentException(Messages.getString("ScriptedCreator.IllegalLanguage", language));
        // }
        this.language = language;
    }

    /**
     * Are we caching the script (default: false)
     * @return Returns the reloadable variable
     */
    public boolean isReloadable()
    {
        return reloadable;
    }

    /**
     * @param reloadable Whether or not to reload the script.
     * The default is <b>true</b>. This parameter is only used if scriptPath is
     * used instead of script.  When reloadable is true, ScriptedCreator will
     * check to see if the script has been modified before returning the
     * existing created class.
     */
    public void setReloadable(boolean reloadable)
    {
        this.reloadable = reloadable;

        if (reloadable)
        {
            setCacheable(false);
        }
    }

    /**
     * Are we using dynamic classes (i.e. classes generated by BeanShell or
     * similar) in which case we want to reuse class defs.
     * @return Returns the useDynamicClasses flag state.
     */
    public boolean isUseDynamicClasses()
    {
        return useDynamicClasses;
    }

    /**
     * Are we using dynamic classes (i.e. classes generated by BeanShell or
     * similar) in which case we want to reuse class defs.
     * @param useDynamicClasses The useDynamicClasses flag state.
     */
    public void setUseDynamicClasses(boolean useDynamicClasses)
    {
        this.useDynamicClasses = useDynamicClasses;
    }

    /**
     * @return Returns the path of the script.
     */
    public String getScriptPath()
    {
        return scriptPath;
    }

    /**
     * @param scriptPath Context reletive path to script.
     */
    public void setScriptPath(String scriptPath)
    {
        if (scriptSrc != null)
        {
            throw new IllegalArgumentException(Messages.getString("ScriptCreator.MultipleScript"));
        }

        this.scriptPath = scriptPath;
    }

    /**
     * @return Whether or not the script (located at scriptPath) has been modified.
     */
    private boolean scriptUpdated()
    {
        if (null == scriptPath)
        {
            return false;
        }

        ServletContext sc = WebContextFactory.get().getServletContext();
        File scriptFile = new File(sc.getRealPath(scriptPath));
        if (scriptModified < scriptFile.lastModified())
        {
            log.debug("Script has been updated.");
            clazz = null; // make sure that this gets re-compiled.
            return true;
        }

        return false;
    }

    /**
     * @return Returns the script.
     * @throws InstantiationException
     */
    public String getScript() throws InstantiationException
    {
        if (scriptSrc != null)
        {
            return scriptSrc;
        }

        if (scriptPath == null)
        {
            throw new InstantiationException(Messages.getString("ScriptedCreator.MissingScript"));
        }

        if (cachedScript != null && (!reloadable || !scriptUpdated()))
        {
            return cachedScript;
        }

        // load the script from the path
        log.debug("Loading Script from Path: " + scriptPath);
        RandomAccessFile in = null;

        try
        {
            ServletContext sc = WebContextFactory.get().getServletContext();
            File scriptFile = new File(sc.getRealPath(scriptPath));

            // This uses the platform default encoding. If there are complaints
            // from people wanting to read script files that are not in the
            // default platform encoding then we will need a new param that is
            // used here.
            scriptModified = scriptFile.lastModified();
            in = new RandomAccessFile(scriptFile, "r");
            byte bytes[] = new byte[(int) in.length()];
            in.readFully(bytes);
            cachedScript = new String(bytes);

            return cachedScript;
        }
        catch (Exception ex)
        {
            log.error(ex.getMessage(), ex);
            throw new InstantiationException(Messages.getString("ScriptCreator.MissingScript"));
        }
        finally
        {
            LocalUtil.close(in);
        }
    }

    /**
     * @param scriptSrc The script to set.
     */
    public void setScript(String scriptSrc)
    {
        if (scriptPath != null)
        {
            throw new IllegalArgumentException(Messages.getString("ScriptCreator.MultipleScript"));
        }

        if (scriptSrc == null || scriptSrc.trim().length() == 0)
        {
            throw new IllegalArgumentException(Messages.getString("ScriptedCreator.MissingScript"));
        }

        this.scriptSrc = scriptSrc;
    }

    /**
     * What sort of class do we create?
     * @param classname The name of the class
     */
    public void setClass(String classname)
    {
        try
        {
            this.clazz = LocalUtil.classForName(classname);
        }
        catch (ClassNotFoundException ex)
        {
            throw new IllegalArgumentException(Messages.getString("Creator.ClassNotFound", classname));
        }
    }

    /* (non-Javadoc)
     * @see org.directwebremoting.Creator#getType()
     */
    public Class getType()
    {
        if (clazz == null || (reloadable && scriptUpdated()))
        {
            try
            {
                clazz = getInstance().getClass();
            }
            catch (InstantiationException ex)
            {
                log.error("Failed to instansiate object to detect type.", ex);
                return Object.class;
            }
        }

        return clazz;
    }

    /* (non-Javadoc)
     * @see org.directwebremoting.Creator#getInstance()
     */
    public Object getInstance() throws InstantiationException
    {
        try
        {
            if (useDynamicClasses && clazz != null)
            {
                return clazz.newInstance();
            }

            BSFManager bsfman = new BSFManager();

            try
            {
                WebContext context = WebContextFactory.get();
                bsfman.declareBean("context", context, context.getClass());
            }
            catch (BSFException ex)
            {
                log.warn("Failed to register WebContext with scripting engine: " + ex.getMessage());
            }

            return bsfman.eval(language, (null == scriptPath ? "dwr.xml" : scriptPath), 0, 0, getScript());
        }
        catch (Exception ex)
        {
            log.error("Error executing script", ex);
            throw new InstantiationException(Messages.getString("Creator.IllegalAccess"));
        }
    }

    /**
     * The log stream
     */
    private static final Logger log = Logger.getLogger(ScriptedCreator.class);

    /**
     * The cached type of object that we are creating.
     */
    private Class clazz = null;

    /**
     * The language that we are scripting in. Passed to BSF.
     */
    private String language = null;

    /**
     * The script that we are asking BSF to execute in order to get an object.
     */
    private String scriptSrc = null;

    /**
     * The path of the script we are asking BSF to execute.
     */
    private String scriptPath = null;

    /**
     * Whether or not to reload the script.  Only used if scriptPath is used.
     * ie: An inline script is not reloadable
     */
    private boolean reloadable = true;

    /**
     * By default we assume that our scripts do not create classes dynamically.
     * If they do then we need to take some special care of them.
     */
    private boolean useDynamicClasses = false;

    /**
     * Script modified time. Only used when scriptPath is used.
     */
    private long scriptModified = -1;

    /**
     * Contents of script loaded from scriptPath
     */
    private String cachedScript;
}