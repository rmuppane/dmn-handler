package org.redhat;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jbpm.process.workitem.bpmn2.AbstractRuleTaskHandler;
import org.jbpm.process.workitem.bpmn2.BusinessRuleTaskHandler;
import org.kie.api.KieServices;
import org.kie.api.builder.KieScanner;
import org.kie.api.command.BatchExecutionCommand;
import org.kie.api.command.Command;
import org.kie.api.command.KieCommands;
import org.kie.api.runtime.ExecutionResults;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.StatelessKieSession;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.runtime.process.WorkItemManager;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.dmn.api.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ResultTypeMapping input attribute introduced. Decision Name=FQCN of Java Class this decision maps to
 * Pre-Approval=com.PreApproval;Customer=com.Customer
 */
public class MyCustomDMNHandler implements WorkItemHandler {


    private static final Logger logger = LoggerFactory.getLogger(MyCustomDMNHandler.class);
    private KieServices kieServices;
    private KieCommands commandsFactory;
    private KieContainer kieContainer;
    private KieScanner kieScanner;
    private ClassLoader classLoader;
    private ObjectMapper mapper;

    public MyCustomDMNHandler(ClassLoader classLoader, String groupId, String artifactId, String version) {
        this(groupId, artifactId, version, -1L);
        this.classLoader = classLoader;
        mapper = new ObjectMapper();
    }

    public MyCustomDMNHandler(String groupId, String artifactId, String version, long scannerInterval) {
        this.kieServices = KieServices.get();
        this.commandsFactory = this.kieServices.getCommands();
        logger.debug("About to create KieContainer for {}, {}, {} with scanner interval {}", new Object[]{groupId, artifactId, version, scannerInterval});
        this.kieContainer = this.kieServices.newKieContainer(this.kieServices.newReleaseId(groupId, artifactId, version));
        if (scannerInterval > 0L) {
            this.kieScanner = this.kieServices.newKieScanner(this.kieContainer);
            this.kieScanner.start(scannerInterval);
            logger.debug("Scanner started for {} with poll interval set to {}", this.kieContainer, scannerInterval);
        }

    }

    public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
        Map<String, Object> parameters = new HashMap(workItem.getParameters());
        String language = (String) parameters.remove("Language");
        String resultTypeMapping = (String) parameters.remove("ResultTypeMapping");

        Map<String, String> resultTypeMappingPairs = new HashMap<String, String>();

        if (resultTypeMapping != null) {

            for (String pairs : resultTypeMapping.split(";")) {
                String[] pair = pairs.split("=");
                resultTypeMappingPairs.put(pair[0], pair[1]);
            }
        }

        HashMap results = new HashMap();


        this.handleDMN(parameters, results);

        logger.info("Custom DMN Handler invoked - results to follow");

        results.forEach((k, v) -> {

            logger.info("DMN Key {}, DMN Value {}", k, v);
        });

        results.keySet().forEach(k -> {

            if (resultTypeMappingPairs.containsKey(k)) {
                try {
                    logger.info("About to marshal {} to {}", k, resultTypeMappingPairs.get(k));
                    transformResult(results, (String) k, resultTypeMappingPairs);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });


        manager.completeWorkItem(workItem.getId(), results);


    }

    private void transformResult(HashMap results, String k, Map<String, String> resultTypeMappingPairs) throws ClassNotFoundException, IOException {

        Object marshalMe = results.get(k);
        Class<?> clazz = Class.forName(resultTypeMappingPairs.get(k), true, classLoader);
        String jsonString = mapper.writeValueAsString(marshalMe);
        Object marshalledObject = mapper.readValue(jsonString,clazz);
        results.put(k,marshalledObject);
    }

    public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
    }

    protected void handleDMN(Map<String, Object> parameters, Map<String, Object> results) {
        String namespace = (String) parameters.remove("Namespace");
        String model = (String) parameters.remove("Model");
        String decision = (String) parameters.remove("Decision");
        DMNRuntime runtime = (DMNRuntime) this.kieContainer.newKieSession().getKieRuntime(DMNRuntime.class);
        DMNModel dmnModel = runtime.getModel(namespace, model);
        if (dmnModel == null) {
            throw new IllegalArgumentException("DMN model '" + model + "' not found with namespace '" + namespace + "'");
        } else {
            DMNResult dmnResult = null;
            DMNContext context = runtime.newContext();
            Iterator var10 = parameters.entrySet().iterator();

            while (var10.hasNext()) {
                Map.Entry<String, Object> entry = (Map.Entry) var10.next();
                context.set((String) entry.getKey(), entry.getValue());
            }

            if (decision != null && !decision.isEmpty()) {
                dmnResult = runtime.evaluateDecisionByName(dmnModel, decision, context);
            } else {
                dmnResult = runtime.evaluateAll(dmnModel, context);
            }

            if (dmnResult.hasErrors()) {
                String errors = (String) dmnResult.getMessages(new DMNMessage.Severity[]{DMNMessage.Severity.ERROR}).stream().map((message) -> {
                    return message.toString();
                }).collect(Collectors.joining(", "));
                throw new RuntimeException("DMN result errors:: " + errors);
            } else {
                results.putAll(dmnResult.getContext().getAll());
            }
        }
    }

}
