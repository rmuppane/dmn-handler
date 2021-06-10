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

        HashMap results = new HashMap();
        this.handleDMN(parameters, results);
        manager.completeWorkItem(workItem.getId(), results);

    }

    private Object transformResult(Object toMarshal, String className) throws ClassNotFoundException, IOException {
        Class<?> clazz = Class.forName(className, true, classLoader);
        System.out.println("##########################");
        logger.info("Clazz Type is {}",clazz.getCanonicalName());
        System.out.println("Clazz Type is ["+clazz.getCanonicalName()+"]");

        String jsonString = mapper.writeValueAsString(toMarshal);

        if (isClassCollection(clazz)){
            Class<?> collGenericclazz = Class.forName("com.redhat.Document", true, classLoader);
            System.out.println("collGenericclazz Type is ["+collGenericclazz.getCanonicalName()+"]");
            System.out.println("##########################");
            return mapper.readValue(jsonString,mapper.getTypeFactory().constructCollectionType(List.class, collGenericclazz));
        }

        
        return mapper.readValue(jsonString,clazz);
    }

    public static boolean isClassCollection(Class c) {
        return Collection.class.isAssignableFrom(c) || Map.class.isAssignableFrom(c);
    }

    public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
    }

    protected void handleDMN(Map<String, Object> parameters, Map<String, Object> results) {
        String namespace = (String) parameters.remove("Namespace");
        String model = (String) parameters.remove("Model");
        String decision = (String) parameters.remove("Decision");
        String resultTypeMapping = (String) parameters.remove("ResultTypeMapping");

        Map<String, String> resultTypeMappingPairs = new HashMap<String, String>();

        if (resultTypeMapping != null) {

            for (String pairs : resultTypeMapping.split(";")) {
                String[] pair = pairs.split("=");
                resultTypeMappingPairs.put(pair[0], pair[1]);
            }
        }
        logger.info("Result Type Mapping set to {}",resultTypeMappingPairs);
        
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


                dmnResult.getContext().getAll().forEach((k,v) -> {
                    logger.info("[BEFORE TRANSFORMING RESULTS] DMN Result Key {}, DMN Result Value {}", k, v);

                    if (resultTypeMappingPairs.containsKey(k)) {
                        try {
                            results.put(k,transformResult(v,resultTypeMappingPairs.get(k)));
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    } else {
                        results.put(k,v);
                    }

                });

            }
        }
    }

}
