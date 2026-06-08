{{- define "wms.name" -}}
{{- .Chart.Name -}}
{{- end -}}

{{- define "wms.namespace" -}}
{{- default .Release.Namespace .Values.namespaceOverride -}}
{{- end -}}

{{- define "wms.image" -}}
{{- printf "%s/%s:%s" .root.Values.image.repositoryPrefix .imageName .root.Values.image.tag -}}
{{- end -}}

{{- define "wms.dbUrl" -}}
{{- printf "jdbc:mysql://mysql:3306/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false" .Values.mysql.database -}}
{{- end -}}
