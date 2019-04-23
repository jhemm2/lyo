/*
 * Copyright (c) 2016-2018 KTH Royal Institute of Technology.
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse
 * Public License v1.0 and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html and the
 * Eclipse Distribution
 * License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 *
 * Omar Kacimi         -  Initial implementation
 * Andrew Berezovskyi  -  Lyo contribution updates
 */
package org.eclipse.lyo.oslc4j.trs.client.handlers;

import com.google.common.base.Strings;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.datatype.DatatypeConfigurationException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.vocabulary.RDF;
import org.eclipse.lyo.core.trs.Base;
import org.eclipse.lyo.core.trs.ChangeEvent;
import org.eclipse.lyo.core.trs.ChangeLog;
import org.eclipse.lyo.core.trs.Creation;
import org.eclipse.lyo.core.trs.Deletion;
import org.eclipse.lyo.core.trs.Modification;
import org.eclipse.lyo.core.trs.Page;
import org.eclipse.lyo.core.trs.TrackedResourceSet;
import org.eclipse.lyo.oslc4j.client.OslcClient;
import org.eclipse.lyo.oslc4j.core.exception.OslcCoreApplicationException;
import org.eclipse.lyo.oslc4j.core.model.AbstractResource;
import org.eclipse.lyo.oslc4j.provider.jena.JenaModelHelper;
import org.eclipse.lyo.oslc4j.trs.client.exceptions.JenaModelException;
import org.eclipse.lyo.oslc4j.trs.client.exceptions.ServerRollBackException;
import org.eclipse.lyo.oslc4j.trs.client.util.ChangeEventComparator;
import org.eclipse.lyo.oslc4j.trs.client.exceptions.RepresentationRetrievalException;
import org.eclipse.lyo.oslc4j.trs.client.util.SparqlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for every TRS provider. Handles all periodic operations for a TRS
 * provider. This is the first version which was developed of a TRS provider
 * which does not support any multi threading of A TRS provider's opreations The
 * TRSProviderMultiThreaded class extends this class with support for
 * multithreading.
 *
 * @author Omar
 */
public class TrsProviderHandler extends TRSTaskHandler {
    private final static Logger log = LoggerFactory.getLogger(TrsProviderHandler.class);

    /**
     * The URI of the last processed change event
     */
    protected URI lastProcessedChangeEventUri;

    /**
     * The entry point URI for the tracked resource set of this provider
     */
    protected String trsUriBase;
    protected ChangeEventListener listener;

    /**
     * @param sparqlUpdateService Set to null to disable the triplestore update
     */
    public TrsProviderHandler(String trsUriBase, OslcClient client,
            String sparqlUpdateService, String sparqlQueryService, String sparqlUsername,
            String sparqlPassword, String providerUsername, String providerPassword) {
        super(client,
                sparqlUpdateService,
                sparqlQueryService,
                sparqlUsername,
                sparqlPassword,
                providerUsername,
                providerPassword
        );
        this.trsUriBase = trsUriBase;
    }

    @Override
    public String toString() {
        return "TrsProviderHandler{" + "trsUriBase='" + trsUriBase + '\'' + '}';
    }

    public void attachListener(ChangeEventListener listener) {
        // TODO Andrew@2018-02-27: make it a list
        if (this.listener != null) {
            throw new IllegalStateException(
                    "A listener has been attached already, a list is not supported yet.");
        }

        this.listener = listener;
    }


    /**
     * Implementation of the method inherited from the TRSTaskHandler class. a
     * call to the periodic processing of the change events is done. If an
     * exception is thrown it's logged and the uri of the last processed change
     * event is set to null. This means that during the next period the
     * processing of the base will be done all over again
     */
    @Override
    protected void processTRSTask() {
        try {
            pollAndProcessChanges();
        } catch (Exception e) {
            log.error("Error polling & processing TRS change logs", e);
            log.debug("Resetting the URI of the last successfully processed event");
            lastProcessedChangeEventUri = null;
        }
    }

    /**
     * Return a list of base objects corresponding to the pages of the base
     * after requesting them from the base url. The base url is retrieved from
     * the trs object passes as a parameter.
     *
     * @param updatedTrs the trs object retrieved after retrieving it using the trs uri
     *
     * @return the pages of the base of this trs provider
     */
    public List<Base> updateBases(TrackedResourceSet updatedTrs)
            throws JenaModelException, IOException, URISyntaxException {
        List<Base> bases = new ArrayList<>();
        URI firstBasePageUri = updatedTrs.getBase();
        Base currentBase = fetchRemoteBase(firstBasePageUri.toString());
        Page nextPage = currentBase.getNextPage();
        bases.add(currentBase);
        while (nextPage != null) {
            URI currentPageUri = nextPage.getNextPage();
            if (isNilUri(currentPageUri)) {
                break;
            }
            currentBase = fetchRemoteBase(currentPageUri.toString());
            bases.add(currentBase);
            nextPage = currentBase.getNextPage();
        }
        return bases;
    }

    /**
     * Return a list of change Lo objects corresponding to the pages of the
     * change log after requesting them from the change log url. The pages of
     * the change log will be requested using the change log segmentation until
     * the last change event which was processed is found. The change log url is
     * retrieved from the trs object passes as a parameter.
     *
     * @param updatedTrs the trs object retrieved after retrieving it using the trs uri
     *
     * @return the pages of the change log of this trs provider
     */
    public List<ChangeLog> fetchUpdatedChangeLogs(TrackedResourceSet updatedTrs)
            throws ServerRollBackException, IOException, JenaModelException,
            URISyntaxException {

        ChangeLog firstChangeLog = updatedTrs.getChangeLog();
        List<ChangeLog> changeLogs = new ArrayList<>();
        boolean foundSyncEvent;

        foundSyncEvent = fetchRemoteChangeLogs(firstChangeLog, changeLogs);
        if (!foundSyncEvent) {
            lastProcessedChangeEventUri = null;
            throw new ServerRollBackException(
                    "The sync event can not be found. The sever provinding the trs at: " +
                            trsUriBase + " seems to " + "have been rollecd back to a previous " +
                            "state");
        }
        return changeLogs;
    }

    /**
     * Request the pages of the change log from the TRS provider sequentially
     * through the traversal of the paging information until the last processed
     * change event is found. Add the fetched change logs to the changeLogs
     * argument. In case the last processed change event is found true is
     * returned otherwise false is returned
     *
     * @param currentChangeLog the first change log from which the next page will be
     *                         retrieved to retrieve the other pages of the change log
     * @param changeLogs       the list of change logs which will be filled with the pages of
     *                         the change log
     *
     * @return true if the last processed change event is found, false otherwise
     */
    public boolean fetchRemoteChangeLogs(ChangeLog currentChangeLog, List<ChangeLog> changeLogs)
            throws JenaModelException, IOException, URISyntaxException {
        boolean foundChangeEvent = false;
        URI previousChangeLog;
        do {
            if (currentChangeLog != null) {
                changeLogs.add(currentChangeLog);
                if (changeLogContainsEvent(lastProcessedChangeEventUri, currentChangeLog)) {
                    foundChangeEvent = true;
                    break;
                }
                previousChangeLog = currentChangeLog.getPrevious();
                currentChangeLog = fetchRemoteChangeLog(previousChangeLog.toString());
            } else {
                break;
            }
        }
        while (!RDF.nil.getURI().equals(previousChangeLog.toString()));
        return foundChangeEvent;
    }

    /**
     * returns true if the change log pojo contains the change event with the
     * given uri and false otherwise
     */
    public boolean changeLogContainsEvent(URI syncPointUri, ChangeLog changeLog) {
        for (ChangeEvent changeEvent : changeLog.getChange()) {
            if (changeEvent.getAbout().equals(syncPointUri)) {
                return true;
            }
        }
        return false;
    }

    /**
     * This is not just a check for existence of the config string but switch between 2 modes.
     * <p>
     * Origi
     *
     * @return whether to mirror all TRS changes in the triplstore
     */
    private boolean sparqUpdateEnabled() {
        return !Strings.isNullOrEmpty(sparqlUpdateService);
    }

    /**
     * remove from the URI list the resources for which an event is already present in the change
     * event list. Done to avoid processing base members uselessly
     *
     * @param compressedChangesList the optimized list of change events
     * @param baseMembers           the members of the base
     */
    @Deprecated
    protected void baseChangeEventsOptimization(List<ChangeEvent> compressedChangesList,
            List<URI> baseMembers) {
        for (ChangeEvent changeEvent : compressedChangesList) {
            URI changedResource = changeEvent.getChanged();
            if (baseMembers.contains(changedResource)) {
                log.debug(
                        "Removing '{}' from the base because it is already in the changelog",
                        changeEvent);
                baseMembers.remove(changedResource);
            }
        }
    }

    /**
     * The main method for a TRS provider. This method consists on the periodic
     * process of processing the new change events since last time and the
     * processing of the base in case this is the first time the TRS provider
     * thread is ran
     */
    public void pollAndProcessChanges()
            throws URISyntaxException, JenaModelException, IOException, ServerRollBackException,
            RepresentationRetrievalException {

        log.info("started dealing with TRS Provider: " + trsUriBase);

        TrackedResourceSet updatedTrs = extractRemoteTrs();
        boolean indexingStage = false;
        List<URI> baseMembers = new ArrayList<>();

        // TODO Andrew@2018-02-28: ensure indexing happens when none was made or cutoff is lost
        /*
        Basically, two things can happen that trigger full reindex:
        1. Some event was not processed successfully (current TRS consumer behaviour).
        2. We are so hopelessly behind we can't locate our last processed element in the changelog.
         */
        if (lastProcessedChangeEventUri == null) {
            // If it is the indexing phase retrieve the representation of the base

            List<Base> bases = updateBases(updatedTrs);

            for (Base base : bases) {
                baseMembers.addAll(base.getMembers());
            }

            lastProcessedChangeEventUri = bases.get(0).getCutoffEvent();
            indexingStage = true;
        }

        // Retrieve all change log pages until the page containing the last processed change event
        List<ChangeLog> changeLogs = fetchUpdatedChangeLogs(updatedTrs);

        /* Optimize the list of changes by removing successive update / creation events for the
        same resource and overwriting update / creation events of a resource with more recent
        deletion events.
         */
        List<ChangeEvent> compressedChanges = optimizedChangesList(changeLogs);

        /*======================================================*
          COMMON CODE END (with concurrent TRS provider handler)
         *======================================================*/

        /* Andrew: why does indexing here happens AFTER the change events are processed WHILE the
         concurrent handler does it first (though the change handlers don't wait for the base to
         be updated, the ExecutorService is fired async there).
         */
        /*
          If it is the indexing stage, remove the resources for which the most
          recent change event has already been processed and then process the
          remaining members
         */
        if (indexingStage) {
//            baseChangeEventsOptimization(compressedChanges, baseMembers);
            // FIXME Andrew@2018-02-28: the base resource gets lost at this stage
            // Andrew@2019-01-15: not sure if I registered any resource losses before
            baseMembers = baseChangeEventsOptimizationSafe(compressedChanges, baseMembers);

            for (URI baseMemberUri : baseMembers) {
                log.debug("Fetching TRS base from {}", baseMemberUri);
                String graphName = baseMemberUri.toString();
                Model baseResourceModel = (Model) fetchTRSRemoteResource(graphName, Model.class);
                if (baseResourceModel != null) {
                    log.debug("Processing base member '{}' creation event", graphName);
                    if (sparqUpdateEnabled()) {
                        SparqlUtil.createGraph(graphName, sparqlUpdateService);
                        SparqlUtil.addTriplesToNamedGraph(baseResourceModel,
                                                          graphName,
                                                          sparqlUpdateService);
                    }
                    // TODO Andrew@2018-02-28: figure out how to pass TRS base indexing event
                    // actually it is possible to generate a Creation event per resource in base!
                    notifyListener(null, baseResourceModel);
                    log.trace("Finished processing base member '{}' creation event", graphName);
                } else {
                    log.warn("Failed to fetch a resource from base {}", baseMemberUri);
                }

            }
        }

        for (ChangeEvent changeEvent : compressedChanges) {
            try {
                processChangeEvent(changeEvent);
                lastProcessedChangeEventUri = changeEvent.getAbout();
            } catch (Exception e) {
                log.error("Error processing {}: ", changeEvent, e);
                return;
            }
        }

        log.info("finished dealing with TRS Provider: " + trsUriBase);

    }

    /**
     * Create the necessary sparql update for processing the change events and
     * send it to the sparql update service
     *
     * @param changeEvent the change event to be processed
     */
    public void processChangeEvent(ChangeEvent changeEvent)
            throws IOException, URISyntaxException {
        URI changed = changeEvent.getChanged();
        log.info("processing resource " + changed.toString() + " change event ");

        Model trsResourceModel = null;
        if (!(changeEvent instanceof Deletion)) {
            trsResourceModel = (Model) fetchTRSRemoteResource(changed.toString(), Model.class);
        }

        updateTriplestore(changeEvent, trsResourceModel);
        notifyListener(changeEvent, trsResourceModel);

        log.info("finished processing resource " + changed.toString() + " change event ");
    }

    private void updateTriplestore(final ChangeEvent changeEvent, final Model trsResourceModel) {
        if (sparqUpdateEnabled()) {
            if (changeEvent instanceof Deletion) {
                SparqlUtil.processChangeEvent(changeEvent, null, sparqlUpdateService);
            } else {
                if (trsResourceModel != null) {
                    SparqlUtil.processChangeEvent(changeEvent,
                                                  trsResourceModel,
                                                  sparqlUpdateService);
                }
            }
        }
        // FIXME Andrew@2018-03-18: danger to skip over unprocessed base items
        lastProcessedChangeEventUri = changeEvent.getAbout();
    }

    /**
     * 1. create an ordered list of change events from the list of change logs
     * given as an argument 2. Cut the list at the last processed change event
     * 3. Optimize the changes list by removing all redundant events for the
     * same resource
     *
     * @param changeLogs the list of change logs containing the change events to be
     *                   processed
     *
     * @return the optimized ordered list of change events
     */
    List<ChangeEvent> optimizedChangesList(List<ChangeLog> changeLogs) {
        Collections.reverse(changeLogs);

        ChangeLog firstChangeLog = changeLogs.get(0);
        List<ChangeEvent> firstChangelogEvents = firstChangeLog.getChange();

        // sort the events, needed for the cut-off later
        firstChangelogEvents.sort(new ChangeEventComparator());
        firstChangeLog.setChange(firstChangelogEvents);

        // TODO Andrew@2018-02-28: just delete this line, getter after setter is some superstition
//        firstChangelogEvents = firstChangeLog.getChange();

        // if the last processed event is in the CL, it must be in the first
        // see 'fetchUpdatedChangeLogs' for details why
        int indexOfSync = -1;
        for (ChangeEvent changeEvent : firstChangelogEvents) {
            if (changeEvent.getAbout().equals(lastProcessedChangeEventUri)) {
                indexOfSync = firstChangelogEvents.indexOf(changeEvent);
                break;
            }
        }

        firstChangelogEvents = firstChangelogEvents.subList(indexOfSync + 1,
                firstChangelogEvents.size()
        );
        firstChangeLog.setChange(firstChangelogEvents);

        List<ChangeEvent> changesToProcess = new ArrayList<>();
        // merge all changelogs, events after the cutoff from the first log are there
        for (ChangeLog changeLog : changeLogs) {
            changesToProcess.addAll(changeLog.getChange());
        }
        changesToProcess.sort(new ChangeEventComparator());

        // NB! Andrew@2018-02-27: this is not going to work for getting all changes via MQTT embedding
        // TODO Andrew@2019-01-15: refactor to support MQTT
        // TODO Andrew@2018-02-27: output warning for the events we missed if compress eliminated anything

        // replace all change events for a single resource with the latest event only
        List<ChangeEvent> compressedChanges = compressChanges(changesToProcess);
        return compressedChanges;
    }

    protected List<URI> baseChangeEventsOptimizationSafe(List<ChangeEvent> compressedChangesList,
            List<URI> baseMembers) {
        // do it once to improve performance actually
        final Set<URI> compressedUris = compressedChangesList.stream()
                                                             .map(AbstractResource::getAbout)
                                                             .collect(Collectors.toSet());
        List<URI> filteredBase = new ArrayList<>(baseMembers.size());

        for (URI baseMember : baseMembers) {
            if (!compressedUris.contains(baseMember)) {
                filteredBase.add(baseMember);
            } else {
                log.debug(
                        "Removing {} from the base because it has been updated since in the " +
                                "changelog",
                        baseMember
                );
            }
        }

        return filteredBase;
    }

    private void notifyListener(final ChangeEvent changeEvent, final Model trsResourceModel) {
        if (listener != null) {
            listener.handleChangeEvent(changeEvent, trsResourceModel);
        }
    }

    /**
     * takes an ordered list of change events to be processed and compressed the
     * list by removing multiple change events for the same resource and keeping
     * only the latest event for that resource
     *
     * @return an ordered optimized list of change events
     */
    protected List<ChangeEvent> compressChanges(List<ChangeEvent> changesToProcess) {
        Map<URI, ChangeEvent> resToChangeEventMap = new HashMap<>();

        for (ChangeEvent changeToProcess : changesToProcess) {
            resToChangeEventMap.put(changeToProcess.getChanged(), changeToProcess);
        }

        // TODO Andrew@2018-02-27: create compressed list in one go
        // Algorithm:
        // - use a hashset to keep track of the resource uris
        // - walk the changesToProcess in reverse
        // - reverse the returned list
        List<ChangeEvent> reducedChangesList;
        if (!resToChangeEventMap.isEmpty()) {
            reducedChangesList = new ArrayList<>(resToChangeEventMap.values());
            reducedChangesList.sort(new ChangeEventComparator());

        } else {
            reducedChangesList = new ArrayList<>();
        }
        return reducedChangesList;
    }

    private boolean isNilUri(URI currentPageUri) {
        return currentPageUri == null || currentPageUri.toString().equals(RDF.nil.getURI());
    }

    /**
     * use osl4j functionality to retrieve a TRS object from the rdf model of
     * the TRS returned by the server
     *
     * @param rdFModel the rdf model
     *
     * @return the TRS pojo extracted from the TRS rdf model
     */
    private TrackedResourceSet extractTrsFromRdfModel(Model rdFModel)
            throws JenaModelException, URISyntaxException {
        log.debug("started extracting tracked resource set from rdf model");
        Object[] trackedResourceSets;

        try {
            trackedResourceSets = JenaModelHelper.fromJenaModel(rdFModel, TrackedResourceSet.class);
        } catch (DatatypeConfigurationException | IllegalAccessException |
                InvocationTargetException | InstantiationException | NoSuchMethodException |
                URISyntaxException | OslcCoreApplicationException e) {
            throw new JenaModelException(e);
        }

        TrackedResourceSet trs = null;

        if (isNotEmptySingletonArray(trackedResourceSets) && trackedResourceSets[0] instanceof
                TrackedResourceSet) {
            trs = (TrackedResourceSet) trackedResourceSets[0];
        }
        ChangeLog trsChangeLog = extractChangeLogFromRdfModel(rdFModel);
        trs.setChangeLog(trsChangeLog);
        log.debug("finished extracting tracked resource set from rdf model");
        return trs;
    }

    /**
     * extract the change log projo from the rdf model of the change log
     * returned by the server
     *
     * @param rdFModel of thr change log
     *
     * @return change log pojo
     */
    private ChangeLog extractChangeLogFromRdfModel(Model rdFModel) throws JenaModelException {
        log.debug("started extracting change log from rdf model");
        Object[] changeLogs;

        Object[] modifications;
        Object[] deletions;
        Object[] creations;

        try {
            changeLogs = JenaModelHelper.fromJenaModel(rdFModel, ChangeLog.class);
            creations = JenaModelHelper.fromJenaModel(rdFModel, Creation.class);
            modifications = JenaModelHelper.fromJenaModel(rdFModel, Modification.class);
            deletions = JenaModelHelper.fromJenaModel(rdFModel, Deletion.class);
        } catch (DatatypeConfigurationException | NoSuchMethodException | URISyntaxException |
                OslcCoreApplicationException | InvocationTargetException | InstantiationException
                | IllegalAccessException e) {
            throw new JenaModelException(e);
        }


        if (isNotEmptySingletonArray(changeLogs) && changeLogs[0] instanceof ChangeLog) {
            ChangeLog changeLog = (ChangeLog) changeLogs[0];
            changeLog.getChange().clear();
            if (isNotEmpty(modifications)) {
                changeLog.getChange().addAll(Arrays.asList((Modification[]) modifications));
            }

            if (isNotEmpty(creations)) {
                changeLog.getChange().addAll(Arrays.asList((Creation[]) creations));
            }

            if (isNotEmpty(deletions)) {
                changeLog.getChange().addAll(Arrays.asList((Deletion[]) deletions));
            }
            log.debug("finished extracting change log set from rdf model");
            return changeLog;
        } else {
            log.warn("the change log was missing; returning an empty one");
            return new ChangeLog();
        }
    }

    private boolean isNotEmptySingletonArray(Object[] changeLogs) {
        return changeLogs != null && changeLogs.length < 2 && changeLogs.length > 0;
    }

    private boolean isNotEmpty(Object[] deletions) {
        return deletions != null && deletions.length > 0;
    }

    /**
     * extract the base projo from the rdf model of the base returned by the
     * server
     *
     * @param rdFModel of the base
     *
     * @return base pojo
     */
    private Base extractBaseFromRdfModel(Model rdFModel) throws JenaModelException {
        log.debug("started extracting base from rdf model");
        Page nextPage;
        Base baseObj = null;
        Object[] nextPageArray;
        Object[] basesArray;
        try {
            nextPageArray = JenaModelHelper.fromJenaModel(rdFModel, Page.class);
            basesArray = JenaModelHelper.fromJenaModel(rdFModel, Base.class);
        } catch (DatatypeConfigurationException | NoSuchMethodException | URISyntaxException |
                OslcCoreApplicationException | InvocationTargetException | InstantiationException
                | IllegalAccessException e) {
            throw new JenaModelException(e);
        }

        if (isNotEmptySingletonArray(basesArray) && basesArray[0] instanceof Base) {
            baseObj = (Base) basesArray[0];
        }

        if (baseObj == null) {
            return null;
        }

        if (isNotEmptySingletonArray(nextPageArray) && nextPageArray[0] instanceof Page) {
            nextPage = (Page) nextPageArray[0];
            baseObj.setNextPage(nextPage);
            log.debug("finished extracting base from rdf model");
            return baseObj;
        }
        log.debug("finished extracting base from rdf model");
        return null;
    }

    /**
     * retieve the trs from the trs provider using the trs uri attribute and
     * return a trs pojo accordingly
     *
     * @return trs pojo
     */
    TrackedResourceSet extractRemoteTrs()
            throws IOException, JenaModelException, URISyntaxException {
        Model rdfModel = (Model) fetchTRSRemoteResource(trsUriBase, Model.class);
        return extractTrsFromRdfModel(rdfModel);
    }

    /**
     * * retrieve the change log from the trs provider using the changeLogURI
     * argument return a change log pojo accordingly
     *
     * @param changeLogURl url of the change log
     *
     * @return change log pojo
     */
    private ChangeLog fetchRemoteChangeLog(String changeLogURl)
            throws IOException, IllegalArgumentException, SecurityException, JenaModelException,
            URISyntaxException {
        Model rdfModel = (Model) fetchTRSRemoteResource(changeLogURl, Model.class);
        return extractChangeLogFromRdfModel(rdfModel);
    }

    /**
     * * retrieve the base from the trs provider using the baseURI argument
     * return a base pojo accordingly
     *
     * @param baseUrl url of the base
     *
     * @return base pojo
     */
    private Base fetchRemoteBase(String baseUrl)
            throws IOException, JenaModelException, URISyntaxException {
        final Model rdFModel = (Model) fetchTRSRemoteResource(baseUrl, Model.class);
        return extractBaseFromRdfModel(rdFModel);
    }

}
