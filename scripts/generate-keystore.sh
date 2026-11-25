#!/bin/bash
# Скрипт для генерации keystore и подготовки secrets для GitHub Actions
# Запуск: ./scripts/generate-keystore.sh

set -e

KEYSTORE_NAME="${1:-release.keystore}"
ALIAS="${2:-fitapp}"
VALIDITY="${3:-10000}"

echo "=== Генерация keystore для FitApp ==="
echo ""

# Запрос паролей
read -s -p "Введите пароль для keystore (минимум 6 символов): " STORE_PASSWORD
echo ""

if [ ${#STORE_PASSWORD} -lt 6 ]; then
    echo "Ошибка: Пароль должен быть минимум 6 символов"
    exit 1
fi

read -s -p "Введите пароль для ключа (или Enter для того же, что keystore): " KEY_PASSWORD
echo ""

if [ -z "$KEY_PASSWORD" ]; then
    KEY_PASSWORD="$STORE_PASSWORD"
fi

# Проверка наличия keytool
if ! command -v keytool &> /dev/null; then
    echo "Ошибка: keytool не найден. Установите JDK и добавьте в PATH."
    exit 1
fi

# Генерация keystore
echo ""
echo "Генерация keystore..."

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KEYSTORE_PATH="$SCRIPT_DIR/../$KEYSTORE_NAME"

keytool -genkey -v \
    -keystore "$KEYSTORE_PATH" \
    -alias "$ALIAS" \
    -keyalg RSA -keysize 2048 \
    -validity "$VALIDITY" \
    -storepass "$STORE_PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    -dname "CN=FitApp, OU=Development, O=FitApp, L=Unknown, ST=Unknown, C=RU"

echo "✓ Keystore создан: $KEYSTORE_PATH"
echo ""

# Кодирование в Base64
echo "Кодирование в Base64..."
BASE64_PATH="$SCRIPT_DIR/../keystore_base64.txt"
base64 -i "$KEYSTORE_PATH" -o "$BASE64_PATH"

echo "✓ Base64 сохранён: $BASE64_PATH"
echo ""

# Вывод инструкций
echo "=== Следующие шаги ==="
echo ""
echo "1. Добавьте secrets в GitHub:"
echo "   Settings → Secrets and variables → Actions"
echo ""
echo "   KEYSTORE_BASE64 = содержимое файла keystore_base64.txt"
echo "   KEYSTORE_PASSWORD = $STORE_PASSWORD"
echo "   KEY_ALIAS = $ALIAS"
echo "   KEY_PASSWORD = $KEY_PASSWORD"
echo ""
echo "2. Для создания релиза:"
echo "   git tag v1.0.0"
echo "   git push origin v1.0.0"
echo ""
echo "⚠ ВАЖНО: Сохраните keystore в безопасном месте!"
echo "   Файл: $KEYSTORE_PATH"
echo "   Без него вы не сможете обновлять приложение."
echo ""
echo "✓ Готово!"

# Очистка переменных
unset STORE_PASSWORD
unset KEY_PASSWORD
