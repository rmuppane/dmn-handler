curl -u anton:password1! -X POST "http://localhost:8080/kie-server/services/rest/server/containers/DMNExperiment/dmn" -H "accept: application/xml" -H "content-type: application/json" -d "{ \"model-namespace\": \"https://kiegroup.org/dmn/_F8F9DC3D-3B89-4171-BFA8-D863AE120DF2\", \"dmn-context\":{ \"Application\": { \"term\":24, \"amount\": 25000 }, \"Customer\": {\"fullName\": \"John Doe\", \"incomeAnnual\": 40000, \"age\": 23} } }"