FROM nginx:1.27-alpine
# Varsayılan: compose ağındaki app container'ı. Debug modunda compose override ile değişir.
ENV API_UPSTREAM=http://app:8080
COPY default.conf.template /etc/nginx/templates/default.conf.template
COPY public /usr/share/nginx/html
EXPOSE 80
