<HTTPSampler guiclass="HttpTestSampleGui" testclass="HTTPSampler" testname="${testname}" enabled="true">
  <elementProp name="HTTPsampler.Arguments" elementType="Arguments" guiclass="HTTPArgumentsPanel" testclass="Arguments" enabled="true">
    <collectionProp name="Arguments.arguments">
      
      <#list nameValuePairs as nv>
      <elementProp name="${nv.name}" elementType="HTTPArgument">
        <boolProp name="HTTPArgument.always_encode">true</boolProp>
        <stringProp name="Argument.name">${nv.name}</stringProp>
        <stringProp name="Argument.value">${nv.value!""}</stringProp>
        <stringProp name="Argument.metadata">=</stringProp>
        <boolProp name="HTTPArgument.use_equals">true</boolProp>
      </elementProp>
      </#list>
      
      <#if includeViewState>
      <elementProp name="javax.faces.ViewState" elementType="HTTPArgument">
        <boolProp name="HTTPArgument.always_encode">false</boolProp>
        <stringProp name="Argument.name">javax.faces.ViewState</stringProp>
        <stringProp name="Argument.value">${r"${jsfViewState}"}</stringProp>
        <stringProp name="Argument.metadata">=</stringProp>
        <boolProp name="HTTPArgument.use_equals">true</boolProp>
      </elementProp>
      </#if>
      
    </collectionProp>
  </elementProp>
  <stringProp name="HTTPSampler.domain"></stringProp>
  <stringProp name="HTTPSampler.port"></stringProp>
  <stringProp name="HTTPSampler.connect_timeout"></stringProp>
  <stringProp name="HTTPSampler.response_timeout"></stringProp>
  <stringProp name="HTTPSampler.protocol"></stringProp>
  <stringProp name="HTTPSampler.contentEncoding">UTF-8</stringProp>
  <stringProp name="HTTPSampler.path">${r"${CONTEXT}"}${path}</stringProp>
  <stringProp name="HTTPSampler.method">${method}</stringProp>
  <boolProp name="HTTPSampler.follow_redirects">true</boolProp>
  <boolProp name="HTTPSampler.auto_redirects">false</boolProp>
  <boolProp name="HTTPSampler.use_keepalive">true</boolProp>
  <boolProp name="HTTPSampler.DO_MULTIPART_POST">false</boolProp>
  <stringProp name="HTTPSampler.FILE_NAME"></stringProp>
  <stringProp name="HTTPSampler.FILE_FIELD"></stringProp>
  <stringProp name="HTTPSampler.mimetype"></stringProp>
  <boolProp name="HTTPSampler.monitor">false</boolProp>
  <stringProp name="HTTPSampler.embedded_url_re"></stringProp>
</HTTPSampler>